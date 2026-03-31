package com.billiard.users;

import com.billiard.shared.web.PageResponse;
import com.billiard.shared.web.ToggleActiveRequest;
import com.billiard.users.dto.UserResponse;
import com.billiard.users.dto.UserUpsertRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserCrudService userCrudService;

    public UserController(UserCrudService userCrudService) {
        this.userCrudService = userCrudService;
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return userCrudService.list(q, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userCrudService.get(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userCrudService.create(request));
    }

    @PutMapping("/{id}")
    public UserResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpsertRequest request
    ) {
        return userCrudService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public UserResponse updateActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request
    ) {
        return userCrudService.updateActive(id, request.active());
    }
}
