package com.jcash.app.users;

import com.jcash.app.security.PinChangeService;
import com.jcash.app.users.dto.ChangePinRequest;
import com.jcash.app.users.dto.CreateUserRequest;
import com.jcash.app.users.dto.UpdateProfileRequest;
import com.jcash.app.users.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PinChangeService pinChangeService;
    private final SecurityContextRepository securityContextRepository;

    public UserController(
            UserService userService,
            PinChangeService pinChangeService,
            SecurityContextRepository securityContextRepository) {
        this.userService = userService;
        this.pinChangeService = pinChangeService;
        this.securityContextRepository = securityContextRepository;
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

    // PUT rather than PATCH: the client always sends all three fields.
    @PutMapping("/me")
    public UserResponse updateCurrentUser(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        User user = userService.updateProfile(
                authentication.getName(),
                request.email(),
                request.mobileNumber(),
                request.displayName()
        );

        if (!user.getEmail().equals(authentication.getName())) {
            renameSessionPrincipal(authentication, user.getEmail(), httpRequest, httpResponse);
        }

        return UserResponse.from(user);
    }

    @PutMapping("/me/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePin(
            @Valid @RequestBody ChangePinRequest request,
            Authentication authentication) {

        pinChangeService.changePin(
                authentication.getName(),
                request.currentPin(),
                request.newPin()
        );
    }

    /**
     * The session remembers the user by email (authentication.getName()), and
     * every endpoint looks the user up by it. After an email change the old name
     * would match nobody, so store an authentication with the new email in the
     * session, the same way AuthController stores one at login.
     */
    private void renameSessionPrincipal(
            Authentication current,
            String newEmail,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Authentication renamed = UsernamePasswordAuthenticationToken.authenticated(
                newEmail, null, current.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(renamed);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }
}
