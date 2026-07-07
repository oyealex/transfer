package com.ecommerce.user.controller;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User address CRUD endpoints. Requires USER role authentication.
 */
@RestController
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping("/api/v1/users/addresses")
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody AddressRequest request,
                                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        AddressResponse response = addressService.createAddress(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/users/addresses")
    public ResponseEntity<List<AddressResponse>> listAddresses(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<AddressResponse> addresses = addressService.listAddresses(userId);
        return ResponseEntity.ok(addresses);
    }

    @PutMapping("/api/v1/users/addresses/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(@PathVariable Long addressId,
                                                          @Valid @RequestBody AddressRequest request,
                                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        AddressResponse response = addressService.updateAddress(userId, addressId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/v1/users/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long addressId,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        addressService.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }
}
