package com.billiard.tables;

import com.billiard.shared.web.PageResponse;
import com.billiard.shared.web.ToggleActiveRequest;
import com.billiard.tables.dto.TableTypeResponse;
import com.billiard.tables.dto.TableTypeUpsertRequest;
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
@RequestMapping("/api/v1/table-types")
@PreAuthorize("hasRole('ADMIN')")
public class TableTypeController {

    private final TableTypeCrudService tableTypeCrudService;

    public TableTypeController(TableTypeCrudService tableTypeCrudService) {
        this.tableTypeCrudService = tableTypeCrudService;
    }

    @GetMapping
    public PageResponse<TableTypeResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return tableTypeCrudService.list(q, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public TableTypeResponse get(@PathVariable Long id) {
        return tableTypeCrudService.get(id);
    }

    @PostMapping
    public ResponseEntity<TableTypeResponse> create(
            @Valid @RequestBody TableTypeUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tableTypeCrudService.create(request));
    }

    @PutMapping("/{id}")
    public TableTypeResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TableTypeUpsertRequest request
    ) {
        return tableTypeCrudService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public TableTypeResponse updateActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request
    ) {
        return tableTypeCrudService.updateActive(id, request.active());
    }
}
