package com.jcash.app.transactions;

import com.jcash.app.security.SecurityConfiguration;
import com.jcash.app.transactions.dto.TransactionHistoryPage;
import com.jcash.app.transactions.exception.InvalidPageRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionHistoryController.class)
@Import(SecurityConfiguration.class)
class TransactionHistoryControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionHistoryService transactionHistoryService;

    @Test
    void defaultsToFirstPageOfTwenty() throws Exception {
        when(transactionHistoryService.findAuthenticatedUserHistory(
                "demo@example.com", 0, 20
        )).thenReturn(new TransactionHistoryPage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/transactions/me")
                        .with(user("demo@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void invalidPageRequestReturns400() throws Exception {
        when(transactionHistoryService.findAuthenticatedUserHistory(
                "demo@example.com", 0, 500
        )).thenThrow(new InvalidPageRequestException(
                TransactionHistoryService.MAX_PAGE_SIZE
        ));

        mockMvc.perform(get("/api/transactions/me")
                        .param("size", "500")
                        .with(user("demo@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PAGE_REQUEST"));
    }
}
