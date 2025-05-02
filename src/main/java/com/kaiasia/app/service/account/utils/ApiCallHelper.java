package com.kaiasia.app.service.account.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * Lớp tiện ích dùng để thực hiện các cuộc gọi API với nhiều tùy chọn cấu hình.
 * Hỗ trợ các phương thức GET, POST và cho phép thêm header, body.
 * Cung cấp một builder để dễ dàng thiết lập các cấu hình cho cuộc gọi API.
 *
 * @param <T> Kiểu dữ liệu của phản hồi từ API.
 */
@Slf4j
@Deprecated
public class    ApiCallHelper<T> {
    // URL của API endpoint
    private String url;

    // Phương thức HTTP (GET, POST, PUT, DELETE)
    private HttpMethod httpMethod;

    // Nội dung body của request (sử dụng cho POST, PUT)
    private String body;

    // Các header HTTP cần thêm vào request
    private HttpHeaders headers;

    // Kiểu dữ liệu của phản hồi mong đợi từ API
    private Class<T> responseType;

    private long readTimeout;

    /**
     * Tạo RestTemplate với cấu hình timeout.
     *
     * @param timeout Thời gian timeout (tính bằng milliseconds).
     * @return Đối tượng RestTemplate được cấu hình timeout.
     */
    private static RestTemplate createRestTemplateWithTimeout(long timeout) { // read timeout # connect timeout
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout);
        requestFactory.setReadTimeout((int) timeout);
        return new RestTemplate(requestFactory);
    }

    /**
     * Thực hiện một cuộc gọi API đơn giản với phương thức GET.
     *
     * @param url          Đường dẫn URL của API.
     * @param responseType Kiểu dữ liệu của phản hồi từ API.
     * @param <T>          Kiểu dữ liệu của phản hồi.
     * @return Phản hồi từ API.
     */
    public static <T> T call(String url, Class<T> responseType, long timeout) {
        return call(url, HttpMethod.GET, responseType, timeout);
    }

    /**
     * Thực hiện cuộc gọi API với URL, phương thức HTTP và kiểu phản hồi được chỉ định.
     *
     * @param url          Đường dẫn URL của API.
     * @param httpMethod   Phương thức HTTP (vd: GET, POST).
     * @param responseType Kiểu dữ liệu của phản hồi từ API.
     * @param <T>          Kiểu dữ liệu của phản hồi.
     * @return Phản hồi từ API.
     */
    public static <T> T call(String url, HttpMethod httpMethod, Class<T> responseType, long timeout) {
        return call(url, httpMethod, "", responseType, timeout);
    }

    /**
     * Thực hiện cuộc gọi API với URL, phương thức HTTP, nội dung body và kiểu phản hồi được chỉ định.
     *
     * @param url          Đường dẫn URL của API.
     * @param httpMethod   Phương thức HTTP (vd: POST, PUT).
     * @param body         Nội dung body của request.
     * @param responseType Kiểu dữ liệu của phản hồi từ API.
     * @param timeout      Thời gian timeout (tính bằng milliseconds).
     * @param <T>          Kiểu dữ liệu của phản hồi.
     * @return Phản hồi từ API.
     */
    public static <T> T call(String url, HttpMethod httpMethod, String body, Class<T> responseType, long timeout) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentLength(body.getBytes().length);
        return call(url, httpMethod, body, headers, responseType, timeout);
    }

    /**
     * Thực hiện cuộc gọi API với các tham số chi tiết.
     *
     * @param url          Đường dẫn URL của API.
     * @param httpMethod   Phương thức HTTP (vd: GET, POST).
     * @param body         Nội dung body của request.
     * @param headers      Header của request.
     * @param responseType Kiểu dữ liệu của phản hồi từ API.
     * @param timeout      Thời gian timeout (tính bằng milliseconds).
     * @param <T>          Kiểu dữ liệu của phản hồi.
     * @return Phản hồi từ API.
     */
    public static <T> T call(String url, HttpMethod httpMethod, String body, MultiValueMap<String, String> headers, Class<T> responseType, long timeout) {
        RestTemplate restTemplate = createRestTemplateWithTimeout(timeout);
        RequestEntity<String> entity = new RequestEntity<>(body, headers, httpMethod, URI.create(url));
        ResponseEntity<T> response = restTemplate.exchange(entity, responseType);
        T apiResponse = response.getBody();
        log.info("Gọi API thành công: " + apiResponse);
        return apiResponse;
    }

    /**
     * Thực hiện cuộc gọi API bằng cách sử dụng các thuộc tính đã được cấu hình.
     *
     * @return Phản hồi từ API.
     */
    public T call()  {
        return call(url, httpMethod, body, headers, responseType, readTimeout);
    }

    /**
     * Tạo một builder để thiết lập các cấu hình cho cuộc gọi API.
     *
     * @param <T> Kiểu dữ liệu của phản hồi từ API.
     * @return Một builder mới.
     */
    public static <T> ApiCallBuilder<T> builder() {
        return new ApiCallBuilder<>();
    }

    /**
     * Lớp builder để hỗ trợ việc xây dựng cấu hình cho ApiCallHelper.
     *
     * @param <Y> Kiểu dữ liệu của phản hồi từ API.
     */
    public static class ApiCallBuilder<Y> {
        private final ApiCallHelper<Y> apiCallHelper;

        private ApiCallBuilder() {
            apiCallHelper = new ApiCallHelper<>();
            apiCallHelper.httpMethod = HttpMethod.GET;
            apiCallHelper.body = "";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            apiCallHelper.headers = headers;
            apiCallHelper.readTimeout = 60000;
        }

        /**
         * Thiết lập URL cho API.
         *
         * @param url Đường dẫn URL của API.
         * @return Đối tượng builder.
         */
        public ApiCallBuilder<Y> url(String url) {
            this.apiCallHelper.url = url;
            return this;
        }

        /**
         * Thiết lập phương thức HTTP.
         *
         * @param httpMethod Phương thức HTTP (vd: GET, POST).
         * @return Đối tượng builder.
         */
        public ApiCallBuilder<Y> httpMethod(HttpMethod httpMethod) {
            this.apiCallHelper.httpMethod = httpMethod;
            return this;
        }

        /**
         * Thiết lập nội dung body của request.
         *
         * @param body Nội dung body.
         * @return Đối tượng builder.
         */
        public ApiCallBuilder<Y> body(String body) {
            this.apiCallHelper.body = body;
            return this;
        }

        /**
         * Thiết lập header cho request.
         *
         * @param headers Header của request.
         * @return Đối tượng builder.
         */
        public ApiCallBuilder<Y> headers(HttpHeaders headers) {
            this.apiCallHelper.headers = headers;
            return this;
        }

        /**
         * Thiết lập kiểu phản hồi mong đợi từ API.
         *
         * @param responseType Kiểu dữ liệu của phản hồi.
         * @return Đối tượng builder.
         */
        public ApiCallBuilder<Y> responseType(Class<Y> responseType) {
            this.apiCallHelper.responseType = responseType;
            return this;
        }

        public ApiCallBuilder<Y> readTimeout(long readTimeout) {
            this.apiCallHelper.readTimeout = readTimeout;
            return this;
        }

        /**
         * Hoàn thành việc xây dựng cấu hình và trả về đối tượng ApiCallHelper.
         *
         * @return Đối tượng ApiCallHelper.
         */
        public ApiCallHelper<Y> build() {
            return apiCallHelper;
        }
    }
}
