package com.kaiasia.app.service.account.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Class này dùng để định nghĩa dữ liệu trả ra từ Customer và cũng có thể trả ra từ T2405
 */
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class AccountOut extends BaseResponse {
    private String customerID;
    private String accountType;
    private String shortName;
    private String shortTitle;
    private String currency;
    private String accountID;
    private String customerType;
    private String altAccount;
    private String category;
    private String company;
    private String availBal;
    private String productCode;
    private String accountStatus;
}
