import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  UseGuards,
  Req,
  Optional,
} from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiBearerAuth,
  ApiOkResponse,
  ApiCreatedResponse,
  ApiQuery,
} from '@nestjs/swagger';
import { FastifyRequest } from 'fastify';
import { CoachingService } from './coaching.service';
import { JwtAuthGuard } from '../identity/auth.guard';
import { CurrentUser, AuthenticatedUser } from '../../common/current-user.decorator';
import { CreateInquiryDto } from './dto/create-inquiry.dto';

@ApiTags('Coaching')
@Controller()
export class CoachingController {
  constructor(private readonly coaching: CoachingService) {}

  @Get('coaches')
  @ApiOperation({ summary: 'List public coach profiles, optionally filtered by sport (padel/pilates)' })
  @ApiQuery({ name: 'sport', required: false, enum: ['padel', 'pilates'] })
  @ApiOkResponse({ description: 'Array of Coach objects with bio, specialties, sport' })
  async getCoaches(@Query('sport') sport?: string): Promise<unknown> {
    return this.coaching.getCoaches(sport);
  }

  @Get('coaches/:slug')
  @ApiOperation({ summary: 'Return full coach profile by URL slug including achievements and photo' })
  async getCoach(@Param('slug') slug: string): Promise<unknown> {
    return this.coaching.getCoachBySlug(slug);
  }

  @Post('coaches/inquiries')
  @ApiOperation({
    summary:
      'Submit a coaching inquiry lead (anonymous allowed). ' +
      'reCAPTCHA token required if not authenticated. Rate-limited: 3/day/email.',
  })
  @ApiCreatedResponse({ description: 'Inquiry stored; ops team notified via email' })
  async createInquiry(
    @Body() dto: CreateInquiryDto,
    @Req() _req: FastifyRequest,
    @Optional() @CurrentUser() user?: AuthenticatedUser,
  ): Promise<unknown> {
    return this.coaching.createInquiry(dto, user?.id);
  }
}
