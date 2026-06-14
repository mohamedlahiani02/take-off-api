import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  UseGuards,
  HttpCode,
  HttpStatus,
  Headers,
  ParseIntPipe,
  DefaultValuePipe,
  ParseUUIDPipe,
} from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiBearerAuth,
  ApiOkResponse,
  ApiCreatedResponse,
  ApiQuery,
} from '@nestjs/swagger';
import { PilatesService } from './pilates.service';
import { JwtAuthGuard } from '../identity/auth.guard';
import { CurrentUser, AuthenticatedUser } from '../../common/current-user.decorator';
import { CreateReservationDto } from './dto/create-reservation.dto';

@ApiTags('Pilates')
@Controller('pilates')
export class PilatesController {
  constructor(private readonly pilates: PilatesService) {}

  @Get('classes')
  @ApiOperation({ summary: 'List all class definitions: Reformer, Mat, Sculpt, Prenatal, Private' })
  @ApiOkResponse({ description: 'Array of ClassDefinition objects' })
  async getClasses(): Promise<unknown> {
    return this.pilates.getClassDefinitions();
  }

  @Get('sessions')
  @ApiOperation({ summary: '7-day rolling session schedule with live spots-left count (public)' })
  @ApiQuery({ name: 'from', required: false, description: 'Start date YYYY-MM-DD (default: today)' })
  @ApiQuery({ name: 'days', required: false, example: 7 })
  async getSessions(
    @Query('from') from = new Date().toISOString().slice(0, 10),
    @Query('days', new DefaultValuePipe(7), ParseIntPipe) days: number,
  ): Promise<unknown> {
    return this.pilates.getSessions(from, days);
  }

  @Post('reservations')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Reserve a spot in a class session. Waitlisted automatically when full.' })
  @ApiCreatedResponse({ description: 'Reservation created with status HELD or WAITLISTED' })
  async createReservation(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: CreateReservationDto,
    @Headers('idempotency-key') idempotencyKey: string,
  ): Promise<unknown> {
    return this.pilates.createReservation(user.id, dto, idempotencyKey);
  }

  @Post('reservations/:id/cancel')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Cancel a pilates reservation; next waitlisted user is promoted automatically' })
  async cancelReservation(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) reservationId: string,
  ): Promise<unknown> {
    return this.pilates.cancelReservation(user.id, reservationId);
  }
}
