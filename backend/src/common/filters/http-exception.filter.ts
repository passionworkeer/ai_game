import {
  ExceptionFilter,
  Catch,
  ArgumentsHost,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { Request, Response } from 'express';
import { ErrorCodes } from '../types';

@Catch()
export class HttpExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(HttpExceptionFilter.name);

  catch(exception: unknown, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<Request>();

    let status: number = HttpStatus.INTERNAL_SERVER_ERROR;
    let code: string = ErrorCodes.INTERNAL_ERROR;
    let message = 'Internal server error';

    if (exception instanceof HttpException) {
      status = exception.getStatus();
      const res = exception.getResponse();

      if (typeof res === 'object' && res !== null) {
        const resObj = res as Record<string, unknown>;
        code = (resObj['code'] as string) || this.getCodeFromStatus(status);
        message =
          (resObj['message'] as string) ||
          (Array.isArray(resObj['message'])
            ? (resObj['message'] as string[]).join(', ')
            : exception.message);
      } else {
        code = this.getCodeFromStatus(status);
        message = exception.message;
      }
    } else if (exception instanceof Error) {
      message = exception.message;
    }

    this.logger.error(
      `${request.method} ${request.url} -> ${status} [${code}] ${message}`,
    );

    response.status(status).json({
      success: false,
      error: {
        code,
        message,
      },
    });
  }

  private getCodeFromStatus(status: number): string {
    switch (status) {
      case 400:
        return ErrorCodes.VALIDATION_ERROR;
      case 401:
        return ErrorCodes.UNAUTHORIZED;
      case 403:
        return 'FORBIDDEN';
      case 404:
        return ErrorCodes.CHARACTER_NOT_FOUND;
      case 409:
        return ErrorCodes.ALREADY_PURCHASED;
      case 422:
        return ErrorCodes.PURCHASE_VERIFY_FAILED;
      default:
        return ErrorCodes.INTERNAL_ERROR;
    }
  }
}
