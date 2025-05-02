package com.kaiasia.app.service.account.configuration;

import lombok.*;

@Setter
@Getter
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DepApiProperties {
    private String url;
    private long timeout;
    private String apiKey;
    private String apiName;
}
