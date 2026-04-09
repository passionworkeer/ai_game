import { NestFactory } from '@nestjs/core';
import { ValidationPipe, Logger } from '@nestjs/common';
import { AppModule } from './app.module';
import { HttpExceptionFilter } from './common/filters/http-exception.filter';

async function bootstrap() {
  const logger = new Logger('Bootstrap');

  const app = await NestFactory.create(AppModule);

  // 全局前缀
  app.setGlobalPrefix('api/v1');

  // 全局校验管道
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,       // 剔除 DTO 中没有定义的字段
      forbidNonWhitelisted: true, // 拒绝多余字段
      transform: true,        // 自动类型转换
      transformOptions: {
        enableImplicitConversion: true,
      },
    }),
  );

  // 全局异常过滤器
  app.useGlobalFilters(new HttpExceptionFilter());

  const port = parseInt(process.env.PORT ?? '3000', 10);
  await app.listen(port);

  logger.log(`Server running on http://localhost:${port}/api/v1`);
}

bootstrap();
