package com.kaiasia.app.service.account.configuration;

import com.kaiasia.app.service.account.model.KaiApiRequestBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KaiApiRequestBuilderFactory {
    @Value("${kai.name}")
    private String curApiName;

    public KaiApiRequestBuilder getBuilder(){
        KaiApiRequestBuilder builder = KaiApiRequestBuilder.builder();
        builder.requestApi(curApiName);
        return builder;
    }
}
