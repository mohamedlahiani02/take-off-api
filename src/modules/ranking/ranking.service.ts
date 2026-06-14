import { Injectable, Logger } from '@nestjs/common';
import { DrizzleService } from '../../infrastructure/drizzle.service';
import { LogMatchDto } from './dto/log-match.dto';

/**
 * RankingService — ELO calculation, match log, monthly ladder.
 *
 * ELO formula (ARCHITECTURE.md §11.3):
 *   delta = (result==W ? +20 : -15) * (1 + 0.15 * (opponentLevel - myLevel))
 *   delta = clamp(delta, -40, +40)
 *   newPoints = max(0, points + delta)
 *   newLevel  = min(7, floor(newPoints / 150) + 1)
 *
 * All written in a single TX: match insert + user update + ladder recalc.
 * A nightly job snapshots the monthly ladder_entry table.
 */
@Injectable()
export class RankingService {
  private readonly logger = new Logger(RankingService.name);

  constructor(private readonly drizzle: DrizzleService) {}

  /**
   * Log a match result, compute ELO delta, update user level.
   * Returns pointsDelta and newLevel.
   * TODO: implement full TX
   */
  async logMatch(
    userId: string,
    dto: LogMatchDto,
  ): Promise<{ pointsDelta: number; newLevel: number; newPoints: number }> {
    // TODO: SELECT points, padel_level FROM identity.users WHERE id = userId FOR UPDATE
    // TODO: compute delta using ELO formula
    // TODO: INSERT INTO ranking.match ...
    // TODO: UPDATE identity.users SET points = ..., padel_level = ...
    // TODO: UPSERT ranking.ladder_entry for current period
    this.logger.log({ userId, result: dto.result }, 'logMatch (TODO)');
    throw new Error('RankingService.logMatch not implemented — TODO');
  }

  /**
   * Return the leaderboard for a period (default: current month).
   * Top 50 entries + caller's own rank.
   * TODO: query ranking.ladder_entry WHERE period = :period ORDER BY points DESC LIMIT 50
   */
  async getLadder(
    _period: string,
    _callerId?: string,
  ): Promise<{ top50: unknown[]; myEntry: unknown | null }> {
    return { top50: [], myEntry: null };
  }

  /**
   * Return the authenticated user's match history.
   * TODO: cursor-paginated query on ranking.match
   */
  async getUserMatches(_userId: string, _cursor?: string): Promise<unknown[]> {
    return [];
  }
}
