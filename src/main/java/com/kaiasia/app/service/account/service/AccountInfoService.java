package com.kaiasia.app.service.account.service;

import com.kaiasia.app.core.utils.GetErrorUtils;
import com.kaiasia.app.register.KaiMethod;
import com.kaiasia.app.register.KaiService;
import com.kaiasia.app.register.Register;
import com.kaiasia.app.service.account.exception.ExceptionHandler;
import com.kaiasia.app.service.account.model.request.Account2In;
import com.kaiasia.app.service.account.model.response.Auth1Out;
import com.kaiasia.app.service.account.model.response.BaseResponse;
import com.kaiasia.app.service.account.model.validation.SuccessGroup;
import com.kaiasia.app.service.account.utils.ObjectAndJsonUtils;
import com.kaiasia.app.service.account.utils.ServiceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms.apiclient.authen.AuthRequest;
import ms.apiclient.authen.AuthTakeSessionResponse;
import ms.apiclient.authen.AuthenClient;
import ms.apiclient.model.*;
import ms.apiclient.t24util.T24AccountInfoResponse;
import ms.apiclient.t24util.T24Request;
import ms.apiclient.t24util.T24UtilClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@KaiService
@Slf4j
@RequiredArgsConstructor
public class AccountInfoService {
    private final GetErrorUtils apiErrorUtils;
    private final RedisTemplate<String, Object> redisTemplate;
    private final T24UtilClient t24UtilClient;
    private final ExceptionHandler exceptionHandler;
    private final AuthenClient authenClient;

    @KaiMethod(name = "getCURR_INFO", type = Register.VALIDATE)
    public ApiError validate(ApiRequest req) {
        return ServiceUtils.validate(req, Account2In.class, apiErrorUtils, "ENQUIRY");
    }

    @KaiMethod(name = "getCURR_INFO")
    public ApiResponse process(ApiRequest request) {
        Account2In requestData = ObjectAndJsonUtils.fromObject(request
                .getBody()
                .get("enquiry"), Account2In.class);
        String location = "AccountInfo-" + requestData.getSessionId() + "-" + System.currentTimeMillis();

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
            String cacheKey = "AccountInfo:" + requestData.getSessionId() + ":" + requestData.getAccountId();

            // Kiểm tra cache
            ApiResponse cachedResponse = getCachedResponse(cacheKey);
            if (cachedResponse != null) {
                log.info("Cache hit for AccountInfo : {}", cacheKey);
                return cachedResponse;
            }

            // Call T24 API
            T24AccountInfoResponse t24AccountInfoResponse = t24UtilClient.getAccountInfo(location,
                    T24Request.builder()
                            .accountId(requestData.getAccountId())
                            .build(),
                    request.getHeader());
            log.warn("{}", t24AccountInfoResponse.getAccountId());
            // **Error Handling for T24 Response**
            if (Objects.nonNull(t24AccountInfoResponse.getError()) && !ApiError.OK_CODE.equals(t24AccountInfoResponse.getError().getCode())) {
                log.error("Error calling T24 API for AccountInfo {} (session {}): {}", requestData.getAccountId(), requestData.getSessionId(), t24AccountInfoResponse.getError());
                response.setError(t24AccountInfoResponse.getError());
                return response;
            }

            HashMap<String, Object> params = new HashMap<>();
            params.put("customerID", t24AccountInfoResponse.getCustomerId());
            params.put("accountType", t24AccountInfoResponse.getAccountType());
            params.put("shortName", t24AccountInfoResponse.getShortName());
            params.put("shortTitle", t24AccountInfoResponse.getShortTitle());
            params.put("currency", t24AccountInfoResponse.getCurrency());
            params.put("accountId", t24AccountInfoResponse.getAccountId());
            params.put("altAccount", t24AccountInfoResponse.getAltAccount());
            params.put("category", t24AccountInfoResponse.getCategory());
            params.put("company", t24AccountInfoResponse.getCompany());
            params.put("availBal", t24AccountInfoResponse.getAvaiBalance());
            params.put("productCode", t24AccountInfoResponse.getProductCode());

            body.put("enquiry", params);
            response.setBody(body);

            // Lưu vào cache
            cacheResponse(cacheKey, response);

            return response;
        }, request, "AccountInfo/" + requestData.getSessionId() + "/" + System.currentTimeMillis());
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
            log.info("Account info cached with key: {}", cacheKey);
        } catch (Exception e) {
            log.error("Error while caching account info: {}", e.getMessage());
        }
    }
}
