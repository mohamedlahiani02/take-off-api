import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { S3Client, PutObjectCommand, GetObjectCommand, DeleteObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';

/**
 * R2Client — S3-compatible client pointing at Cloudflare R2.
 *
 * Two buckets are used:
 *   - MEDIA: user-facing uploads (product images, coach photos)
 *   - BACKUPS: pgBackRest archives (not touched by the API; managed by sidecar)
 *
 * See INFRASTRUCTURE.md §2 (Cloudflare R2 free tier) and §8 (secrets).
 */
@Injectable()
export class R2Client implements OnModuleInit {
  private readonly logger = new Logger(R2Client.name);
  public s3!: S3Client;
  public mediaBucket!: string;
  public backupBucket!: string;

  constructor(private readonly config: ConfigService) {}

  onModuleInit(): void {
    this.mediaBucket = this.config.getOrThrow<string>('R2_BUCKET_MEDIA');
    this.backupBucket = this.config.getOrThrow<string>('R2_BUCKET_BACKUPS');

    this.s3 = new S3Client({
      region: 'auto',
      endpoint: this.config.getOrThrow<string>('R2_ENDPOINT'),
      credentials: {
        accessKeyId: this.config.getOrThrow<string>('R2_ACCESS_KEY'),
        secretAccessKey: this.config.getOrThrow<string>('R2_SECRET_KEY'),
      },
    });

    this.logger.log('R2 (S3) client initialised');
  }

  /** Generate a pre-signed PUT URL for direct browser upload. */
  async presignUpload(key: string, contentType: string, expiresIn = 300): Promise<string> {
    const command = new PutObjectCommand({
      Bucket: this.mediaBucket,
      Key: key,
      ContentType: contentType,
    });
    return getSignedUrl(this.s3, command, { expiresIn });
  }

  /** Generate a pre-signed GET URL for private assets. */
  async presignDownload(key: string, expiresIn = 3600): Promise<string> {
    const command = new GetObjectCommand({ Bucket: this.mediaBucket, Key: key });
    return getSignedUrl(this.s3, command, { expiresIn });
  }

  /** Delete an object from the media bucket. */
  async deleteMedia(key: string): Promise<void> {
    await this.s3.send(new DeleteObjectCommand({ Bucket: this.mediaBucket, Key: key }));
  }
}
