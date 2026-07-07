package com.ecommerce.user.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserAddress;
import com.ecommerce.user.entity.UserStatus;
import com.ecommerce.user.query.AddressDto;
import com.ecommerce.user.query.UserDto;
import com.ecommerce.user.query.UserQueryService;
import com.ecommerce.user.repository.UserAddressRepository;
import com.ecommerce.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link UserQueryService} providing cross-module query capabilities.
 */
@Service
@Transactional(readOnly = true)
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;

    public UserQueryServiceImpl(UserRepository userRepository,
                                UserAddressRepository userAddressRepository) {
        this.userRepository = userRepository;
        this.userAddressRepository = userAddressRepository;
    }

    @Override
    public UserDto getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return toDto(user);
    }

    @Override
    public boolean isActive(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    public boolean isFrozen(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getStatus() == UserStatus.FROZEN)
                .orElse(false);
    }

    @Override
    public AddressDto getDefaultAddress(Long userId) {
        List<UserAddress> addresses = userAddressRepository.findByUserId(userId);
        return addresses.stream()
                .filter(UserAddress::isDefault)
                .findFirst()
                .map(this::toDto)
                .orElse(null);
    }

    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setUserId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setNickname(user.getNickname());
        dto.setStatus(user.getStatus().name());
        dto.setRole(user.getRole().name());
        return dto;
    }

    private AddressDto toDto(UserAddress address) {
        AddressDto dto = new AddressDto();
        dto.setAddressId(address.getId());
        dto.setProvince(address.getProvince());
        dto.setCity(address.getCity());
        dto.setDistrict(address.getDistrict());
        dto.setDetail(address.getDetail());
        dto.setReceiverName(address.getReceiverName());
        dto.setReceiverPhone(address.getReceiverPhone());
        return dto;
    }
}
