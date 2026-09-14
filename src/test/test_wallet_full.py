"""
Full test suite for the Wallet App — functional + concurrency.
Runs as a single command against the live deployment.

Usage:
    python test_wallet_full.py
    python test_wallet_full.py --concurrency 50
    python test_wallet_full.py --concurrency 100 --only concurrency
    python test_wallet_full.py --skip-warmup

Requires: pip install requests
"""

import argparse
import random
import threading
import uuid
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests

session = requests.Session()
RUN_ID = uuid.uuid4().hex[:8]

BASE_URL = "https://wallet-779l.onrender.com"


# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------

def log(title, response, verbose=True):
    if verbose:
        print(f"\n--- {title} ---")
        print(f"Status: {response.status_code}")
        try:
            print(f"Body: {response.json()}")
        except ValueError:
            print(f"Body: {response.text}")
    return response


def create_user(username, verbose=True):
    resp = session.post(f"{BASE_URL}/user", json={"userName": username})
    return log(f"Create user '{username}'", resp, verbose)


def auth_headers(token):
    return {"Authorization": f"Bearer {token}"}


def create_wallet(user_id, amount, token, verbose=True):
    resp = session.post(
        f"{BASE_URL}/wallet",
        json={"userId": user_id, "amount": amount},
        headers=auth_headers(token),
    )
    return log(f"Get-or-create wallet for user {user_id}", resp, verbose)


def get_balance(wallet_id, token, verbose=True):
    resp = session.get(f"{BASE_URL}/wallet/{wallet_id}", headers=auth_headers(token))
    return log(f"Get balance for wallet {wallet_id}", resp, verbose)


def make_transfer(from_id, to_id, amount, token, idempotency_key=None, verbose=True):
    headers = auth_headers(token)
    if idempotency_key is not None:
        headers["idempotencyKey"] = idempotency_key
    resp = session.post(
        f"{BASE_URL}/transfers",
        json={"fromId": from_id, "toId": to_id, "amount": amount},
        headers=headers,
    )
    return log(f"Transfer {amount} from {from_id} to {to_id}", resp, verbose)


def fire_with_barrier(fn, args_list):
    """Blocks all threads at a barrier so they fire genuinely simultaneously."""
    n = len(args_list)
    barrier = threading.Barrier(n)
    results = [None] * n

    def worker(i, args):
        barrier.wait()
        results[i] = fn(*args)

    threads = [threading.Thread(target=worker, args=(i, args)) for i, args in enumerate(args_list)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    return results


def warm_up():
    """Render free tier cold-starts after idling — fire one throwaway request first."""
    print("Warming up (cold start may take up to a minute on Render free tier)...")
    try:
        create_user(f"warmup-{RUN_ID}", verbose=False)
        print("Warm-up complete.\n")
    except Exception as e:
        print(f"Warm-up request failed (continuing anyway): {e}\n")


# ---------------------------------------------------------------------------
# PART 1: Functional tests
# ---------------------------------------------------------------------------

def run_functional_tests():
    print("\n" + "=" * 70)
    print("PART 1: FUNCTIONAL TESTS")
    print("=" * 70)

    user_a = create_user(f"alice-{RUN_ID}").json()
    user_b = create_user(f"bob-{RUN_ID}").json()

    token_a = user_a["token"]
    token_b = user_b["token"]
    user_a_id = user_a["id"]
    user_b_id = user_b["id"]

    wallet_a = create_wallet(user_a_id, 1000, token_a).json()
    wallet_b = create_wallet(user_b_id, 0, token_b).json()

    wallet_a_id = wallet_a["id"]
    wallet_b_id = wallet_b["id"]

    wallet_a_again = create_wallet(user_a_id, 999, token_a).json()
    assert wallet_a_again["id"] == wallet_a_id
    assert wallet_a_again["balance"] == 1000, "get-or-create should NOT reset balance"

    bal_a_initial = get_balance(wallet_a_id, token_a).json()
    bal_b_initial = get_balance(wallet_b_id, token_b).json()
    assert bal_a_initial == 1000
    assert bal_b_initial == 0

    resp = make_transfer(wallet_a_id, wallet_b_id, 100, token_a, idempotency_key=None)
    assert resp.status_code == 400, "missing idempotencyKey should return 400"

    key1 = str(uuid.uuid4())
    resp = make_transfer(wallet_a_id, wallet_b_id, 200, token_a, idempotency_key=key1)
    assert resp.status_code == 200 and resp.text == "SUCCESSFUL"

    bal_a = get_balance(wallet_a_id, token_a).json()
    bal_b = get_balance(wallet_b_id, token_b).json()
    assert bal_a == 800, f"expected 800, got {bal_a}"
    assert bal_b == 200, f"expected 200, got {bal_b}"

    resp = make_transfer(wallet_a_id, wallet_b_id, 200, token_a, idempotency_key=key1)
    assert resp.status_code == 200 and resp.text == "SUCCESSFUL"
    assert get_balance(wallet_a_id, token_a).json() == 800, "retry must not double-debit"

    resp = make_transfer(wallet_a_id, wallet_b_id, 999, token_a, idempotency_key=key1)
    assert resp.status_code == 409, "reused key + different body must be 409"

    key2 = str(uuid.uuid4())
    resp = make_transfer(wallet_a_id, wallet_b_id, 100000, token_a, idempotency_key=key2)
    assert resp.status_code >= 400
    assert get_balance(wallet_a_id, token_a).json() == 800, "failed transfer must not change balance"

    key3 = str(uuid.uuid4())
    resp = make_transfer(f"nonexistent-wallet-{RUN_ID}", wallet_b_id, 50, token_a, idempotency_key=key3)
    assert resp.status_code == 404

    resp = requests.get(f"{BASE_URL}/wallet/{wallet_a_id}")
    assert resp.status_code == 401, "no token should be 401"

    resp = requests.get(f"{BASE_URL}/wallet/{wallet_a_id}", headers=auth_headers("garbage-token"))
    assert resp.status_code == 401, "invalid token should be 401"

    resp = session.post(
        f"{BASE_URL}/transfers",
        json={"fromId": wallet_a_id, "toId": wallet_b_id, "amount": 10},
        headers={**auth_headers(token_a), "idempotencyKey": ""},
    )
    assert resp.status_code == 400, "blank idempotencyKey should be 400"

    print("\nPART 1 PASSED.")


# ---------------------------------------------------------------------------
# PART 2: Concurrency tests
# ---------------------------------------------------------------------------

def test_concurrent_get_or_create_wallet(n):
    print(f"\n=== Concurrent get-or-create wallet (N={n}) ===")

    user = create_user(f"concurrent-create-{RUN_ID}", verbose=False).json()
    token = user["token"]
    user_id = user["id"]
    seed_amount = 500

    args_list = [(user_id, seed_amount, token, False)] * n
    responses = fire_with_barrier(create_wallet, args_list)

    statuses = Counter(r.status_code for r in responses)
    wallet_ids = {r.json()["id"] for r in responses if r.status_code == 200}

    print(f"Status codes: {dict(statuses)} | Distinct wallet IDs: {wallet_ids}")

    assert all(r.status_code == 200 for r in responses)
    assert len(wallet_ids) == 1, f"expected exactly one wallet, got {len(wallet_ids)}"

    final_balance = get_balance(next(iter(wallet_ids)), token, verbose=False).json()
    assert final_balance == seed_amount

    print("PASSED: exactly one wallet created under concurrency.")


def test_idempotent_retry_storm(k):
    print(f"\n=== Idempotent retry storm (K={k}) ===")

    user_a = create_user(f"retrystorm-a-{RUN_ID}", verbose=False).json()
    user_b = create_user(f"retrystorm-b-{RUN_ID}", verbose=False).json()

    seed_a = 1000
    amount = 150

    wallet_a = create_wallet(user_a["id"], seed_a, user_a["token"], verbose=False).json()
    wallet_b = create_wallet(user_b["id"], 0, user_b["token"], verbose=False).json()

    idempotency_key = str(uuid.uuid4())

    args_list = [(wallet_a["id"], wallet_b["id"], amount, user_a["token"], idempotency_key, False)] * k
    responses = fire_with_barrier(make_transfer, args_list)

    status_counts = Counter(r.status_code for r in responses)
    body_texts = {r.text for r in responses}

    print(f"Status codes: {dict(status_counts)} | Distinct bodies: {body_texts}")

    assert all(r.status_code == 200 for r in responses)
    assert body_texts == {"SUCCESSFUL"}

    final_bal_a = get_balance(wallet_a["id"], user_a["token"], verbose=False).json()
    final_bal_b = get_balance(wallet_b["id"], user_b["token"], verbose=False).json()

    assert final_bal_a == seed_a - amount, f"possible double debit: {final_bal_a}"
    assert final_bal_b == amount, f"possible double credit: {final_bal_b}"

    print(f"PASSED: exactly one debit/credit despite {k} concurrent retries.")


def test_conservation_under_contention(num_wallets, num_transfers, seed_amount, max_transfer_amount, max_workers):
    print(f"\n=== Conservation under contention (wallets={num_wallets}, transfers={num_transfers}) ===")

    users = [create_user(f"contention-{i}-{RUN_ID}", verbose=False).json() for i in range(num_wallets)]
    wallets = [create_wallet(u["id"], seed_amount, u["token"], verbose=False).json() for u in users]

    total_before = sum(get_balance(w["id"], users[i]["token"], verbose=False).json() for i, w in enumerate(wallets))
    print(f"Total balance before: {total_before}")

    rng = random.Random(42)
    args_list = []
    for _ in range(num_transfers):
        from_idx = rng.randrange(num_wallets)
        to_idx = rng.randrange(num_wallets)
        while to_idx == from_idx:
            to_idx = rng.randrange(num_wallets)
        amount = rng.randint(1, max_transfer_amount)
        args_list.append((wallets[from_idx]["id"], wallets[to_idx]["id"], amount, users[from_idx]["token"], str(uuid.uuid4()), False))

    with ThreadPoolExecutor(max_workers=min(num_transfers, max_workers)) as pool:
        futures = [pool.submit(make_transfer, *args) for args in args_list]
        responses = [f.result() for f in as_completed(futures)]

    status_counts = Counter(r.status_code for r in responses)
    print(f"Status codes: {dict(status_counts)}")

    unexpected = [r for r in responses if r.status_code not in (200, 400, 404, 409)]
    assert not unexpected, f"unexpected status codes: {[r.status_code for r in unexpected]}"

    balances = [get_balance(w["id"], users[i]["token"], verbose=False).json() for i, w in enumerate(wallets)]
    total_after = sum(balances)

    print(f"Final balances: {balances} | Total after: {total_after}")

    assert total_after == total_before, f"money created/destroyed: {total_before} -> {total_after}"
    assert all(b >= 0 for b in balances), f"a wallet went negative: {balances}"

    print("PASSED: total balance conserved, no wallet went negative.")


def test_distinct_keys_same_body_all_apply(n):
    print(f"\n=== Distinct keys, identical bodies (N={n}) ===")

    user_a = create_user(f"distinctkeys-a-{RUN_ID}", verbose=False).json()
    user_b = create_user(f"distinctkeys-b-{RUN_ID}", verbose=False).json()

    seed = n * 100 + 500
    amount = 50

    wallet_a = create_wallet(user_a["id"], seed, user_a["token"], verbose=False).json()
    wallet_b = create_wallet(user_b["id"], 0, user_b["token"], verbose=False).json()

    args_list = [(wallet_a["id"], wallet_b["id"], amount, user_a["token"], str(uuid.uuid4()), False) for _ in range(n)]
    responses = fire_with_barrier(make_transfer, args_list)

    assert all(r.status_code == 200 and r.text == "SUCCESSFUL" for r in responses)

    final_bal_a = get_balance(wallet_a["id"], user_a["token"], verbose=False).json()
    final_bal_b = get_balance(wallet_b["id"], user_b["token"], verbose=False).json()

    assert final_bal_a == seed - (amount * n)
    assert final_bal_b == amount * n

    print(f"PASSED: all {n} distinct-key transfers applied independently.")


def test_concurrent_overdraw_race(n, wallet_balance, transfer_amount):
    print(f"\n=== Concurrent overdraw race (N={n}, balance={wallet_balance}, amount={transfer_amount}) ===")

    user_a = create_user(f"overdraw-a-{RUN_ID}", verbose=False).json()
    user_b = create_user(f"overdraw-b-{RUN_ID}", verbose=False).json()

    wallet_a = create_wallet(user_a["id"], wallet_balance, user_a["token"], verbose=False).json()
    wallet_b = create_wallet(user_b["id"], 0, user_b["token"], verbose=False).json()

    args_list = [(wallet_a["id"], wallet_b["id"], transfer_amount, user_a["token"], str(uuid.uuid4()), False) for _ in range(n)]
    responses = fire_with_barrier(make_transfer, args_list)

    successes = [r for r in responses if r.status_code == 200 and r.text == "SUCCESSFUL"]
    failures = [r for r in responses if r.status_code != 200]

    expected_successes = min(n, wallet_balance // transfer_amount)
    expected_failures = n - expected_successes

    print(f"Successes: {len(successes)} (expected {expected_successes}) | Failures: {len(failures)} (expected {expected_failures})")

    assert len(successes) == expected_successes
    assert len(failures) == expected_failures

    final_bal_a = get_balance(wallet_a["id"], user_a["token"], verbose=False).json()
    final_bal_b = get_balance(wallet_b["id"], user_b["token"], verbose=False).json()

    expected_final_a = wallet_balance - (expected_successes * transfer_amount)
    expected_final_b = expected_successes * transfer_amount

    assert final_bal_a == expected_final_a
    assert final_bal_a >= 0, "wallet went negative"
    assert final_bal_b == expected_final_b

    print(f"PASSED: exactly {expected_successes} of {n} concurrent transfers succeeded; no negative balance.")


def run_concurrency_tests(concurrency):
    print("\n" + "=" * 70)
    print(f"PART 2: CONCURRENCY TESTS (concurrency={concurrency})")
    print("=" * 70)

    test_concurrent_get_or_create_wallet(n=concurrency)
    test_idempotent_retry_storm(k=concurrency)
    test_conservation_under_contention(
        num_wallets=4, num_transfers=100,
        seed_amount=5000, max_transfer_amount=50,
        max_workers=50,
    )
    test_distinct_keys_same_body_all_apply(n=concurrency)
    test_concurrent_overdraw_race(n=concurrency, wallet_balance=concurrency * 10, transfer_amount=10)

    print("\nPART 2 PASSED.")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Wallet App test suite — functional + concurrency, one command.")
    parser.add_argument("--concurrency", type=int, default=20, help="Number of simultaneous requests for concurrency scenarios (default: 20)")
    parser.add_argument("--only", choices=["functional", "concurrency", "all"], default="all", help="Run only functional tests, only concurrency tests, or all (default: all)")
    parser.add_argument("--skip-warmup", action="store_true", help="Skip the cold-start warm-up request")
    args = parser.parse_args()

    print(f"=== Wallet App test run — RUN_ID: {RUN_ID} ===")
    print(f"Target: {BASE_URL}")
    print(f"Concurrency level: {args.concurrency}")

    if not args.skip_warmup:
        warm_up()

    if args.only in ("functional", "all"):
        run_functional_tests()

    if args.only in ("concurrency", "all"):
        run_concurrency_tests(concurrency=args.concurrency)

    print("\n\nALL TESTS PASSED.")


if __name__ == "__main__":
    main()