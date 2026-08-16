package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.service.PlanService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanControllerValidationTest {

    private final PlanService planService = mock(PlanService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PlanController(planService))
            .build();

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"waitingDays\":6}",
            "{\"waitingDays\":31}",
            "{\"waitingDays\":null}"
    })
    void updateReleasePolicyReturnsBadRequestForInvalidRange(String body) throws Exception {
        mockMvc.perform(put("/api/plans/1/release-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(planService);
    }
}
