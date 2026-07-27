package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.dto.response.IapProductResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IapProductService {

    private final IapProductMapper iapProductMapper;

    public List<IapProductResponse> getAvailableProducts() {
        return iapProductMapper.findActive().stream()
                .map(p -> new IapProductResponse(p.getId(), p.getName(), p.getHuaweiProductId(),
                        p.getIapProductType(), p.getPrice(), p.getCurrency(), p.getSubscriptionType(),
                        p.getName()))
                .toList();
    }

    public IapProductEntity getById(Long id) {
        IapProductEntity product = iapProductMapper.findById(id);
        if (product == null) {
            throw new BusinessException("IAP 商品不存在");
        }
        return product;
    }
}
