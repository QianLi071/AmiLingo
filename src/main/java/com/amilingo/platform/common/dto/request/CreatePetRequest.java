package com.amilingo.platform.common.dto.request;

import lombok.Data;

@Data
public class CreatePetRequest {
    private String name;
    private String description;
}
