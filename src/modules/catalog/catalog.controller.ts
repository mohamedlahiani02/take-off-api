import { Controller, Get, Param, Query } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiOkResponse } from '@nestjs/swagger';
import { CatalogService } from './catalog.service';
import { ProductQueryDto } from './dto/product-query.dto';

@ApiTags('Store / Catalog')
@Controller('store/products')
export class CatalogController {
  constructor(private readonly catalog: CatalogService) {}

  @Get()
  @ApiOperation({ summary: 'Paginated product list with optional category filter and search (pg_trgm)' })
  @ApiOkResponse({ description: 'Cursor-paginated product array' })
  async getProducts(@Query() query: ProductQueryDto): Promise<unknown> {
    return this.catalog.getProducts(query);
  }

  @Get(':slug')
  @ApiOperation({ summary: 'Product detail with all size variants and stock levels' })
  @ApiOkResponse({ description: 'Single Product with variants[]' })
  async getProduct(@Param('slug') slug: string): Promise<unknown> {
    return this.catalog.getProductBySlug(slug);
  }
}
