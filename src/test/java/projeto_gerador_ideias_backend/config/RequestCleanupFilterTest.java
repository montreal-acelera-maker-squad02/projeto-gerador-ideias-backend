package projeto_gerador_ideias_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import projeto_gerador_ideias_backend.service.UserCacheService;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestCleanupFilterTest {

    @Mock
    private UserCacheService userCacheService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RequestCleanupFilter requestCleanupFilter;

    @BeforeEach
    void setUp() {
        requestCleanupFilter = new RequestCleanupFilter(userCacheService);
    }

    @Test
    void shouldCallClearRequestCacheAfterFilterChain() throws ServletException, IOException {
        requestCleanupFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verify(userCacheService, times(1)).clearRequestCache();
    }

    @Test
    void shouldCallClearRequestCacheEvenWhenExceptionOccurs() throws ServletException, IOException {
        doThrow(new ServletException("Test exception")).when(filterChain).doFilter(request, response);

        assertThrows(ServletException.class, () -> {
            requestCleanupFilter.doFilterInternal(request, response, filterChain);
        });

        verify(filterChain, times(1)).doFilter(request, response);
        verify(userCacheService, times(1)).clearRequestCache();
    }

    @Test
    void shouldCallClearRequestCacheEvenWhenIOExceptionOccurs() throws ServletException, IOException {
        doThrow(new IOException("Test exception")).when(filterChain).doFilter(request, response);

        assertThrows(IOException.class, () -> {
            requestCleanupFilter.doFilterInternal(request, response, filterChain);
        });

        verify(filterChain, times(1)).doFilter(request, response);
        verify(userCacheService, times(1)).clearRequestCache();
    }

    @Test
    void shouldCallClearRequestCacheInFinallyBlock() throws ServletException, IOException {
        doThrow(new RuntimeException("Unexpected runtime exception")).when(filterChain).doFilter(request, response);

        assertThrows(RuntimeException.class, () -> {
            requestCleanupFilter.doFilterInternal(request, response, filterChain);
        });

        verify(filterChain, times(1)).doFilter(request, response);
        verify(userCacheService, times(1)).clearRequestCache();
    }
}


