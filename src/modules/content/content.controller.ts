import { Controller, Get } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiOkResponse } from '@nestjs/swagger';
import { ContentService } from './content.service';

@ApiTags('Content')
@Controller('content')
export class ContentController {
  constructor(private readonly content: ContentService) {}

  @Get('partners')
  @ApiOperation({ summary: 'Return active partner list with logos and URLs (public, ETag-cached)' })
  @ApiOkResponse({ description: 'Array of Partner objects' })
  async getPartners(): Promise<unknown> {
    return this.content.getPartners();
  }

  @Get('faq')
  @ApiOperation({ summary: 'Return FAQ items in display order (public)' })
  @ApiOkResponse({ description: 'Array of FAQ items with question and answer' })
  async getFaq(): Promise<unknown> {
    return this.content.getFaq();
  }

  @Get('hours')
  @ApiOperation({ summary: 'Return club opening hours per day-of-week for both Padel and Pilates (public)' })
  @ApiOkResponse({ description: 'Object with padel and pilates hour maps keyed by day' })
  async getHours(): Promise<unknown> {
    return this.content.getHours();
  }
}
