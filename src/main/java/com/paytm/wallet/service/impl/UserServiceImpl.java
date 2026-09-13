package com.paytm.wallet.service.impl;

import com.paytm.wallet.model.User;
import com.paytm.wallet.repository.UserRepository;
import com.paytm.wallet.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public User createUser(String userName) {
        Optional<User> userOptional = userRepository.findByUserName(userName);
        if (userOptional.isPresent()) return userOptional.get();
        User newUser = new User();
        newUser.setUserName(userName.trim().toLowerCase());
        newUser.setToken(UUID.randomUUID().toString());
        return userRepository.save(newUser);
    }
}
