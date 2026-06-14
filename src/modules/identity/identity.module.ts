import { Module } from '@nestjs/common';
import { PassportModule } from '@nestjs/passport';
import { JwtModule } from '@nestjs/jwt';
import { IdentityController } from './identity.controller';
import { IdentityService } from './identity.service';
import { JwtStrategy } from './jwt.strategy';
import { LocalStrategy } from './local.strategy';

@Module({
  imports: [
    PassportModule,
    JwtModule.register({}), // Key loaded dynamically in IdentityService
  ],
  controllers: [IdentityController],
  providers: [IdentityService, JwtStrategy, LocalStrategy],
  exports: [IdentityService],
})
export class IdentityModule {}
