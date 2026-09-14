package com.paytm.wallet.controller;

import com.paytm.wallet.model.User;
import com.paytm.wallet.model.request.UserRequest;
import com.paytm.wallet.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody UserRequest userRequest) {
        log.info("event=api_request endpoint=POST_user userName={}", userRequest.getUserName());
        return ResponseEntity.ok(userService.createUser(userRequest.getUserName()));
    }
}
