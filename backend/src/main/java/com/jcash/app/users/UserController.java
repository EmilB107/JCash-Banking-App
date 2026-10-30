package com.jcash.app.users;

import com.jcash.app.users.dto.CreateUserRequest;
import com.jcash.app.users.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(
            @Valid @RequestBody CreateUserRequest request) {

        User user = userService.createUser(
                request.email(),
                request.mobileNumber(),
                request.displayName(),
                request.pin()
        );

        return UserResponse.from(user);
    }

    @GetMapping("/me")
    public UserResponse currentUser(
            Authentication authentication) {

        return UserResponse.from(
                userService.findAuthenticatedUser(authentication.getName())
        );
    }
}
