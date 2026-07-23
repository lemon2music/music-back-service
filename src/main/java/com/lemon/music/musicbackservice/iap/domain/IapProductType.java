package com.lemon.music.musicbackservice.iap.domain;

/** IAP 商品类型，code 对应华为 PurchaseData.type 数字。 */
public enum IapProductType {
    CONSUMABLE(0), NON_CONSUMABLE(1), AUTORENEWABLE(2), NONRENEWABLE(3);

    private final int code;

    IapProductType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static IapProductType fromCode(int code) {
        for (IapProductType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown IapProductType code: " + code);
    }
}
