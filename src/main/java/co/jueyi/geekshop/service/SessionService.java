package co.jueyi.geekshop.service;

import co.jueyi.geekshop.common.Constant;
import co.jueyi.geekshop.common.RequestContext;
import co.jueyi.geekshop.common.utils.TimeSpanUtil;
import co.jueyi.geekshop.common.utils.TokenUtil;
import co.jueyi.geekshop.config.session_cache.CachedSession;
import co.jueyi.geekshop.config.session_cache.CachedSessionUser;
import co.jueyi.geekshop.config.session_cache.SessionCacheStrategy;
import co.jueyi.geekshop.entity.SessionEntity;
import co.jueyi.geekshop.mapper.SessionEntityMapper;
import co.jueyi.geekshop.types.common.Permission;
import co.jueyi.geekshop.types.role.Role;
import co.jueyi.geekshop.types.user.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Created on Nov, 2020 by @author bobo
 */
@Service
public class SessionService {
    private final SessionCacheStrategy sessionCacheStrategy;
    private final ConfigService configService;
    private final long sessionDurationInMs;

    private final SessionEntityMapper sessionEntityMapper;
    private final UserService userService;

    public SessionService(
            ConfigService configService,
            SessionEntityMapper sessionEntityMapper,
            UserService userService) {
        this.configService = configService;
        this.sessionCacheStrategy = this.configService.getAuthConfig().getSessionCacheStrategy();
        this.sessionDurationInMs = TimeSpanUtil.toMs(this.configService.getAuthOptions().getSessionDuration());
        this.sessionEntityMapper = sessionEntityMapper;
        this.userService = userService;
    }

    // TODO
    // If Role changes, potentially all the cached permissions in the
    // session cache will be wrong, so we just clear the entire cache. It should however
    // be a very rate occurrence in normal operation, once initial setup is complete.
    // this.sessionCacheStrategy.clear();

    /**
     * Authenticates a user's credentials and if okay, creates a new session.
     */
    public CachedSession createNewAuthenticatedSession(
            RequestContext ctx, User user, String authenticationStrategyName) {
        String token = this.generateSessionToken();
        // TODO order handling
        SessionEntity sessionEntity = new SessionEntity();
        sessionEntity.setToken(token);
        sessionEntity.setUserId(user.getId());
        sessionEntity.setAuthenticationStrategy(authenticationStrategyName);
        sessionEntity.setExpires(this.getExpiryDate(this.sessionDurationInMs));
        sessionEntity.setInvalided(false);
        sessionEntity.setAnonymous(false);

        this.sessionEntityMapper.insert(sessionEntity);

        CachedSession authenticatedSession = this.serializeSession(sessionEntity, user);
        this.sessionCacheStrategy.set(authenticatedSession);

        return authenticatedSession;
    }

    /**
     * Create an anonymous session.
     */
    public CachedSession createAnonymousSession() {
        String token = this.generateSessionToken();
        long anonymousSessionDurationInMs = TimeSpanUtil.toMs(Constant.DEFAULT_ANONYMOUS_SESSION_DURATION);
        SessionEntity newSession = new SessionEntity();
        newSession.setToken(token);
        newSession.setExpires(this.getExpiryDate(anonymousSessionDurationInMs));
        newSession.setInvalided(false);
        newSession.setAnonymous(true);
        // save the new session
        this.sessionEntityMapper.insert(newSession);
        CachedSession serializedSession = this.serializeSession(newSession, null);
        this.sessionCacheStrategy.set(serializedSession);
        return serializedSession;
    }

    public CachedSession getSessionFromToken(String sessionToken) {
        CachedSession serializedSession = this.sessionCacheStrategy.get(sessionToken);
        boolean stale = serializedSession != null &&
                serializedSession.getCacheExpiry() < new Date().getTime() / 1000;
        boolean expired = serializedSession != null && serializedSession.getExpires().getTime() < new Date().getTime();

        if (serializedSession != null && !stale && !expired) return serializedSession;

        SessionEntity session = this.findSessionByToken(sessionToken);
        if (session == null) return null;

        User user = null;
        if (session.getUserId() != null) {
            user = this.userService.findUserWithRolesById(session.getUserId());
        }
        serializedSession = this.serializeSession(session, user);
        return serializedSession;
    }

    /**
     * Deletes all existing sessions for the given userId.
     */
    public void deleteSessionByUserId(Long userId) {
        QueryWrapper<SessionEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SessionEntity::getUserId, userId);
        List<SessionEntity> sessionEntityList = this.sessionEntityMapper.selectList(queryWrapper);
        sessionEntityList.forEach(sessionEntity -> this.sessionCacheStrategy.delete(sessionEntity.getToken()));
        this.sessionEntityMapper.delete(queryWrapper);
    }

    /**
     * Looks for a valid session with the given token and returns one if found.
     */
    private SessionEntity findSessionByToken(String token) {
        QueryWrapper<SessionEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SessionEntity::isInvalided, false).eq(SessionEntity::getToken, token);
        SessionEntity session = this.sessionEntityMapper.selectOne(queryWrapper);
        if (session != null && session.getExpires().getTime() > new Date().getTime()) {
            this.updateSessionExpiry(session);
        }
        return session;
    }

    /**
     * If we are over half way to the current session's expiry date, then we update it.
     *
     * This ensures that the session will not expire when in active use, but prevents us from
     * needing to run an update query on *every* request.
     */
    private void updateSessionExpiry(SessionEntity session) {
        long now = new Date().getTime();
        if (session.getExpires().getTime() - now < this.sessionDurationInMs / 2) {
            Date newExpiryDate = this.getExpiryDate(this.sessionDurationInMs);
            session.setExpires(newExpiryDate);
            this.sessionEntityMapper.updateById(session);
        }
    }

    private CachedSession serializeSession(SessionEntity session, User user) {
        long expiry = new Date().getTime() / 1000 + this.configService.getAuthOptions().getSessionCacheTTL();
        CachedSession serializedSession = new CachedSession();
        serializedSession.setCacheExpiry(expiry);
        serializedSession.setId(session.getId());
        serializedSession.setToken(session.getToken());
        serializedSession.setExpires(session.getExpires());
        serializedSession.setActiveOrderId(session.getActiveOrderId());
        if (!session.isAnonymous() && user != null) { // authenticated session
            serializedSession.setAuthenticationStrategy(session.getAuthenticationStrategy());
            CachedSessionUser cachedSessionUser = new CachedSessionUser();
            cachedSessionUser.setId(user.getId());
            cachedSessionUser.setIdentifier(user.getIdentifier());
            cachedSessionUser.setVerified(user.getVerified());
            cachedSessionUser.setPermissions(getUserPermissions(user));
        }
        return serializedSession;
    }

    private List<Permission> getUserPermissions(User user) {
        Set<Permission> permissionSet = new HashSet<>();
        for (Role role : user.getRoles()) {
            permissionSet.addAll(role.getPermissions());
        }
        return new ArrayList<>(permissionSet);
    }

    /**
     * Returns a future expiry date according to timeToExpireInMs in the future.
     */
    private Date getExpiryDate(long timeToExpireInMs) {
        return new Date(System.currentTimeMillis() + timeToExpireInMs);
    }

    private String generateSessionToken() {
        return TokenUtil.generateNewToken(32);
    }
}