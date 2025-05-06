package com.kaiasia.app.service.account.service;//package com.kaiasia.app.service.account.service;


import com.kaiasia.app.core.utils.GetErrorUtils;
import com.kaiasia.app.register.KaiMethod;
import com.kaiasia.app.register.KaiService;
import com.kaiasia.app.register.Register;
import com.kaiasia.app.service.account.exception.ExceptionHandler;
import com.kaiasia.app.service.account.model.request.AccountIn;
import com.kaiasia.app.service.account.model.response.Auth1Out;
import com.kaiasia.app.service.account.model.response.BaseResponse;
import com.kaiasia.app.service.account.model.validation.SuccessGroup;
import com.kaiasia.app.service.account.utils.ObjectAndJsonUtils;
import com.kaiasia.app.service.account.utils.ServiceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms.apiclient.account.Account;
import ms.apiclient.authen.AuthRequest;
import ms.apiclient.authen.AuthTakeSessionResponse;
import ms.apiclient.authen.AuthenClient;
import ms.apiclient.model.*;
import ms.apiclient.t24util.T24CustomerAccountResponse;
import ms.apiclient.t24util.T24Request;
import ms.apiclient.t24util.T24UtilClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@KaiService
@Slf4j
@RequiredArgsConstructor
public class AccountService {
    private final GetErrorUtils apiErrorUtils;
    private final RedisTemplate<String, Object> redisTemplate;
    private final T24UtilClient t24UtilClient;
    private final ExceptionHandler exceptionHandler;
    private final AuthenClient authenClient;


    @KaiMethod(name = "getAccList", type = Register.VALIDATE)
    public ApiError validate(ApiRequest req) {
        return ServiceUtils.validate(req, AccountIn.class, apiErrorUtils, "ENQUIRY");
    }

    @KaiMethod(name = "getAccList")
    public ApiResponse process(ApiRequest request) {
        AccountIn requestData = ObjectAndJsonUtils.fromObject(request
                .getBody()
                .get("enquiry"), AccountIn.class);
        String location = "AccountList-" + requestData.getSessionId() + "-" + System.currentTimeMillis();

        return exceptionHandler.handle(req -> {
            ApiResponse response = new ApiResponse();
            ApiHeader header = new ApiHeader();
            response.setHeader(header);
            ApiBody body = new ApiBody();

//             Call Auth-1 api
            AuthTakeSessionResponse auth1Response = null;
            try {
                auth1Response = authenClient.takeSession(location,
                        AuthRequest.builder()
                                .sessionId(requestData.getSessionId())
                                .build(),
                        request.getHeader());
            } catch (Exception e) {
                throw new RestClientException(location, e);
            }

            if (auth1Response.getError() != null && !ApiError.OK_CODE.equals(auth1Response.getError().getCode())) {
                log.error("{}:{}", location + "#After call Auth-1", auth1Response.getError());
                response.setError(auth1Response.getError());
                return response;
            }

            // Kiểm tra kết quả trả về đủ field không.
            BaseResponse validateAuth1Error = ServiceUtils.validate(ObjectAndJsonUtils.fromObject(auth1Response, Auth1Out.class), SuccessGroup.class);
            if (!validateAuth1Error.getCode().equals(ApiError.OK_CODE)) {
                log.error("{}:{}", location + "#After call Auth-1", validateAuth1Error);
                response.setError(new ApiError(validateAuth1Error.getCode(), validateAuth1Error.getDesc()));
                return response;
            }
            // Tạo cache key
            String cacheKey = "Account:" + requestData.getSessionId() + ":" + requestData.getCustomerID();

            // Kiểm tra cache
            ApiResponse cachedResponse = getCachedResponse(cacheKey);
            if (cachedResponse != null) {
                log.info("Cache hit for account list: {}", cacheKey);
				cachedResponse.setHeader(header);
                return cachedResponse;
            }

			log.info("Cache miss for account list: {}", cacheKey);
            // Call T24 API
            T24CustomerAccountResponse t24CustomerAccountResponse = t24UtilClient.getCustomerAccount(location,
                    T24Request.builder()
                            .customerId(requestData.getCustomerID())
//                            .customerId("281692")
                            .build(),
                    request.getHeader());
            log.warn("{}", t24CustomerAccountResponse.getAccounts());
            // **Error Handling for T24 Response**
            if (Objects.nonNull(t24CustomerAccountResponse.getError()) && !ApiError.OK_CODE.equals(t24CustomerAccountResponse.getError().getCode())) {
                log.error("Error calling T24 API for AccountInfo {} (session {}): {}", requestData.getCustomerID(), requestData.getSessionId(), t24CustomerAccountResponse.getError());
                response.setError(t24CustomerAccountResponse.getError());
                return response;
            }


            HashMap<String, Object> enquiry = new HashMap<>();
            List<HashMap<String, Object>> accountList = new ArrayList<>();
            for (Account account : t24CustomerAccountResponse.getAccounts()) {
                HashMap<String, Object> accountData = new HashMap<>();
                accountData.put("customerID", account.getCustomerId());
                accountData.put("accountType", account.getAccountType());
                accountData.put("shortName", account.getShortName());
                accountData.put("shortTitle", account.getShortTitle());
                accountData.put("currency", account.getCurrency());
                accountData.put("accountID", account.getAccountId());
                accountData.put("customerType", "Vip");
                accountData.put("altAccount", account.getAltAccount());
                accountData.put("category", "1011");
                accountData.put("company", account.getCompany());
                accountData.put("availBal", "200021022");
                accountData.put("productCode", "800");
                accountData.put("accountStatus", account.getAccountStatus());
                accountList.add(accountData);
            }

            enquiry.put("accounts", accountList);
            enquiry.put("status", "OK");

            header.setReqType("RESPONSE");
            body.put("enquiry", enquiry);
            response.setBody(body);

            // Lưu vào cache
            cacheResponse(cacheKey, response);

            return response;
        }, request, "AccountList/" + requestData.getSessionId() + "/" + System.currentTimeMillis());
    }

    private ApiResponse getCachedResponse(String cacheKey) {
        try {
            Object cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                return (ApiResponse) cachedData;
            }
        } catch (Exception e) {
            log.error("Error while accessing Redis: {}", e.getMessage());
        }
        return null;
    }

    private void cacheResponse(String cacheKey, ApiResponse response) {
        try {
            redisTemplate.opsForValue().set(cacheKey, ObjectAndJsonUtils.toJson(response), 30, TimeUnit.MINUTES); // Lưu cache trong 30 phút
            log.info("Account list cached with key: {}", cacheKey);
        } catch (Exception e) {
            log.error("Error while caching account list: {}", e.getMessage());
        }
    }
}