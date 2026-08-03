package org.traccar.api;

import com.google.inject.Provider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traccar.api.security.PermissionsService;
import org.traccar.database.StatisticsManager;
import org.traccar.helper.SessionHelper;
import org.traccar.model.Image;
import org.traccar.storage.Storage;
import org.traccar.storage.query.Request;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UploadsFilterTest {

    private Storage storage;
    private StatisticsManager statisticsManager;
    private PermissionsService permissionsService;
    private UploadsFilter filter;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    public void setUp() {
        storage = mock(Storage.class);
        statisticsManager = mock(StatisticsManager.class);
        permissionsService = mock(PermissionsService.class);
        Provider<PermissionsService> provider = () -> permissionsService;
        filter = new UploadsFilter(storage, statisticsManager, provider);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    private void authenticate(long userId) {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(SessionHelper.ORIGIN_KEY)).thenReturn(null);
        when(session.getAttribute(SessionHelper.USER_ID_KEY)).thenReturn(userId);
        when(request.getSession(false)).thenReturn(session);
    }

    @Test
    public void testUnauthenticatedRequestRejected() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getPathInfo()).thenReturn("/1/photo.jpg");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    public void testSessionWithoutUserRejected() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(SessionHelper.USER_ID_KEY)).thenReturn(null);
        when(request.getSession(false)).thenReturn(session);
        when(request.getPathInfo()).thenReturn("/1/photo.jpg");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    public void testNonNumericIdForbiddenNotServerError() throws Exception {
        authenticate(1);
        when(request.getPathInfo()).thenReturn("/not-a-number/photo.jpg");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    public void testMissingPathForbidden() throws Exception {
        authenticate(1);
        when(request.getPathInfo()).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    public void testUnknownImageForbidden() throws Exception {
        authenticate(1);
        when(request.getPathInfo()).thenReturn("/42/photo.jpg");
        when(storage.getObject(eq(Image.class), any(Request.class))).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    public void testPermittedImagePassesThrough() throws Exception {
        authenticate(1);
        when(request.getPathInfo()).thenReturn("/42/photo.jpg");
        Image image = new Image();
        image.setId(42);
        when(storage.getObject(eq(Image.class), any(Request.class))).thenReturn(image);

        filter.doFilter(request, response, chain);

        verify(permissionsService).checkPermission(Image.class, 1L, 42L);
        verify(statisticsManager).registerRequest(1L);
        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt());
    }

    @Test
    public void testDeniedPermissionForbidden() throws Exception {
        authenticate(2);
        when(request.getPathInfo()).thenReturn("/42/photo.jpg");
        Image image = new Image();
        image.setId(42);
        when(storage.getObject(eq(Image.class), any(Request.class))).thenReturn(image);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        doThrow(new SecurityException("Image access denied"))
                .when(permissionsService).checkPermission(eq(Image.class), anyLong(), anyLong());

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
        assertTrue(body.toString().contains("Image access denied"));
    }

}
