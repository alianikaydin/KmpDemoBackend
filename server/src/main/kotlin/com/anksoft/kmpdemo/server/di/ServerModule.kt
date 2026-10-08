package com.anksoft.kmpdemo.server.di

import com.anksoft.kmpdemo.server.auth.data.ExposedRefreshTokenRepository
import com.anksoft.kmpdemo.server.auth.data.ExposedUserRepository
import com.anksoft.kmpdemo.server.auth.domain.AccessTokenIssuer
import com.anksoft.kmpdemo.server.auth.domain.PasswordHasher
import com.anksoft.kmpdemo.server.auth.domain.RefreshTokenGenerator
import com.anksoft.kmpdemo.server.auth.repository.RefreshTokenRepository
import com.anksoft.kmpdemo.server.auth.repository.UserRepository
import com.anksoft.kmpdemo.server.auth.security.Argon2PasswordHasher
import com.anksoft.kmpdemo.server.auth.security.JwtAccessTokenIssuer
import com.anksoft.kmpdemo.server.auth.security.JwtConfig
import com.anksoft.kmpdemo.server.auth.security.SecureRefreshTokenGenerator
import com.anksoft.kmpdemo.server.auth.service.AuthService
import com.anksoft.kmpdemo.server.config.AppSettings
import com.anksoft.kmpdemo.server.consent.data.ExposedConsentDecisionRepository
import com.anksoft.kmpdemo.server.consent.data.ExposedConsentTextRepository
import com.anksoft.kmpdemo.server.consent.repository.ConsentDecisionRepository
import com.anksoft.kmpdemo.server.consent.repository.ConsentTextRepository
import com.anksoft.kmpdemo.server.consent.service.ConsentService
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.dsl.module
import java.time.Clock

fun serverModule(settings: AppSettings, db: Database, clock: Clock): Module = module {
    single {
        JwtConfig(settings.jwtSecret, settings.jwtIssuer, settings.jwtAudience, settings.accessTokenTtl)
    }
    single<PasswordHasher> { Argon2PasswordHasher() }
    single<AccessTokenIssuer> { JwtAccessTokenIssuer(get()) }
    single<RefreshTokenGenerator> { SecureRefreshTokenGenerator() }
    single<UserRepository> { ExposedUserRepository(db) }
    single<RefreshTokenRepository> { ExposedRefreshTokenRepository(db) }
    single<ConsentTextRepository> { ExposedConsentTextRepository(db) }
    single<ConsentDecisionRepository> { ExposedConsentDecisionRepository(db) }
    single { ConsentService(get(), get(), clock) }
    single {
        AuthService(get(), get(), get(), get(), get(), clock, settings.refreshTokenTtl, consents = get())
    }
}
