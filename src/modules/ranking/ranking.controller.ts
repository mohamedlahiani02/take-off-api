import {
  Controller,
  Get,
  Post,
  Body,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth, ApiOkResponse, ApiCreatedResponse, ApiQuery } from '@nestjs/swagger';
import { RankingService } from './ranking.service';
import { JwtAuthGuard } from '../identity/auth.guard';
import { CurrentUser, AuthenticatedUser } from '../../common/current-user.decorator';
import { LogMatchDto } from './dto/log-match.dto';

@ApiTags('Ranking')
@Controller()
export class RankingController {
  constructor(private readonly ranking: RankingService) {}

  @Post('me/matches')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Log a padel match result. Computes ELO delta and updates user level.' })
  @ApiCreatedResponse({ description: '{ pointsDelta, newLevel, newPoints }' })
  async logMatch(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: LogMatchDto,
  ): Promise<unknown> {
    return this.ranking.logMatch(user.id, dto);
  }

  @Get('padel/ladder')
  @ApiOperation({ summary: 'Return the monthly leaderboard (top 50) and the authenticated caller's own rank row' })
  @ApiQuery({ name: 'period', required: false, description: 'Period YYYY-MM (default: current month)', example: '2026-06' })
  @ApiOkResponse({ description: '{ top50: LadderEntry[], myEntry: LadderEntry | null }' })
  async getLadder(
    @Query('period') period = new Date().toISOString().slice(0, 7),
    @CurrentUser() user?: AuthenticatedUser,
  ): Promise<unknown> {
    return this.ranking.getLadder(period, user?.id);
  }

  @Get('me/matches')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Return cursor-paginated match history for the authenticated user' })
  async getMatches(
    @CurrentUser() user: AuthenticatedUser,
    @Query('cursor') cursor?: string,
  ): Promise<unknown> {
    return this.ranking.getUserMatches(user.id, cursor);
  }
}
