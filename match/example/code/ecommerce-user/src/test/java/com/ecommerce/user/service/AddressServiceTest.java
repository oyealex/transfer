package com.ecommerce.user.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.entity.UserAddress;
import com.ecommerce.user.repository.UserAddressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressService")
class AddressServiceTest {

    @Mock
    private UserAddressRepository userAddressRepository;

    @InjectMocks
    private AddressService addressService;

    private AddressRequest addressRequest(boolean isDefault) {
        AddressRequest request = new AddressRequest();
        request.setProvince("浙江");
        request.setCity("杭州");
        request.setDistrict("西湖区");
        request.setDetail("文三路478号");
        request.setReceiverName("张三");
        request.setReceiverPhone("13900139000");
        request.setDefault(isDefault);
        return request;
    }

    private UserAddress savedAddress(Long id, Long userId, boolean isDefault) {
        UserAddress address = new UserAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setProvince("浙江");
        address.setCity("杭州");
        address.setDistrict("西湖区");
        address.setDetail("文三路478号");
        address.setReceiverName("张三");
        address.setReceiverPhone("13900139000");
        address.setDefault(isDefault);
        return address;
    }

    // --- Create ---

    @Test
    @DisplayName("creates an address and returns AddressResponse")
    void testCreateAddress_returnsAddressResponse() {
        AddressRequest request = addressRequest(false);
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> {
            UserAddress addr = invocation.getArgument(0);
            addr.setId(10L);
            return addr;
        });

        AddressResponse response = addressService.createAddress(1L, request);

        assertThat(response.getAddressId()).isEqualTo(10L);
        assertThat(response.getProvince()).isEqualTo("浙江");
        assertThat(response.getCity()).isEqualTo("杭州");
        assertThat(response.getDistrict()).isEqualTo("西湖区");
        assertThat(response.getDetail()).isEqualTo("文三路478号");
        assertThat(response.getReceiverName()).isEqualTo("张三");
        assertThat(response.getReceiverPhone()).isEqualTo("13900139000");
        assertThat(response.isDefault()).isFalse();
    }

    @Test
    @DisplayName("clears existing default when creating a new default address")
    void testCreateAddress_default_cancelsPreviousDefaults() {
        UserAddress oldDefault = savedAddress(1L, 1L, true);
        AddressRequest request = addressRequest(true);

        when(userAddressRepository.findByUserId(1L)).thenReturn(List.of(oldDefault));
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> {
            UserAddress addr = invocation.getArgument(0);
            addr.setId(2L);
            return addr;
        });

        addressService.createAddress(1L, request);

        assertThat(oldDefault.isDefault()).isFalse();
        verify(userAddressRepository).save(oldDefault);
    }

    // --- List ---

    @Test
    @DisplayName("returns list of addresses for a user")
    void testListAddresses_returnsAddressList() {
        UserAddress addr1 = savedAddress(1L, 1L, true);
        UserAddress addr2 = savedAddress(2L, 1L, false);
        addr2.setCity("宁波");

        when(userAddressRepository.findByUserId(1L)).thenReturn(List.of(addr1, addr2));

        List<AddressResponse> responses = addressService.listAddresses(1L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getAddressId()).isEqualTo(1L);
        assertThat(responses.get(1).getAddressId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("returns empty list when user has no addresses")
    void testListAddresses_emptyList() {
        when(userAddressRepository.findByUserId(1L)).thenReturn(Collections.emptyList());

        List<AddressResponse> responses = addressService.listAddresses(1L);

        assertThat(responses).isEmpty();
    }

    // --- Update ---

    @Test
    @DisplayName("updates an existing address and returns AddressResponse")
    void testUpdateAddress_returnsUpdatedAddress() {
        UserAddress existing = savedAddress(1L, 1L, false);
        AddressRequest update = addressRequest(false);
        update.setDetail("新的地址详情");
        update.setReceiverName("李四");

        when(userAddressRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userAddressRepository.save(any(UserAddress.class))).thenReturn(existing);

        AddressResponse response = addressService.updateAddress(1L, 1L, update);

        assertThat(response.getDetail()).isEqualTo("新的地址详情");
        assertThat(response.getReceiverName()).isEqualTo("李四");
        verify(userAddressRepository).save(existing);
    }

    @Test
    @DisplayName("clears old defaults when updating an address to be default")
    void testUpdateAddress_setDefault_cancelsOldDefaults() {
        UserAddress existing = savedAddress(1L, 1L, false);
        UserAddress oldDefault = savedAddress(2L, 1L, true);
        AddressRequest update = addressRequest(true);

        when(userAddressRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userAddressRepository.findByUserId(1L)).thenReturn(List.of(existing, oldDefault));
        when(userAddressRepository.save(any(UserAddress.class))).thenReturn(existing);

        addressService.updateAddress(1L, 1L, update);

        assertThat(oldDefault.isDefault()).isFalse();
        verify(userAddressRepository).save(oldDefault);
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when updating address not belonging to user")
    void testUpdateAddress_wrongUser_throwsException() {
        UserAddress otherUserAddress = savedAddress(1L, 999L, false);
        AddressRequest update = addressRequest(false);

        when(userAddressRepository.findById(1L)).thenReturn(Optional.of(otherUserAddress));

        assertThatThrownBy(() -> addressService.updateAddress(1L, 1L, update))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userAddressRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when updating a non-existent address")
    void testUpdateAddress_notFound_throwsException() {
        AddressRequest update = addressRequest(false);
        when(userAddressRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(1L, 999L, update))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- Delete ---

    @Test
    @DisplayName("deletes an address belonging to the user")
    void testDeleteAddress_deletesSuccessfully() {
        UserAddress address = savedAddress(1L, 1L, false);
        when(userAddressRepository.findById(1L)).thenReturn(Optional.of(address));

        addressService.deleteAddress(1L, 1L);

        verify(userAddressRepository).delete(address);
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when deleting address not belonging to user")
    void testDeleteAddress_wrongUser_throwsException() {
        UserAddress otherUserAddress = savedAddress(1L, 999L, false);
        when(userAddressRepository.findById(1L)).thenReturn(Optional.of(otherUserAddress));

        assertThatThrownBy(() -> addressService.deleteAddress(1L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userAddressRepository, never()).delete(any());
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when deleting a non-existent address")
    void testDeleteAddress_notFound_throwsException() {
        when(userAddressRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(1L, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
