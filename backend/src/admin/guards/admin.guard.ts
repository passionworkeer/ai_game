import {
  Injectable,
  CanActivate,
  ExecutionContext,
  ForbiddenException,
} from '@nestjs/common';
import { CurrentUserPayload } from '../../common/types';

/**
 * AdminGuard — 检查 JWT payload 中的 role === 'admin'
 *
 * 使用方式：
 *   @UseGuards(JwtAuthGuard, AdminGuard)
 *
 * JwtAuthGuard 先验证 JWT 有效性，
 * AdminGuard 再检查角色是否为 admin。
 */
@Injectable()
export class AdminGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest();
    const user = request.user as CurrentUserPayload | undefined;

    if (!user || user.role !== 'admin') {
      throw new ForbiddenException({
        code: 'FORBIDDEN',
        message: '需要管理员权限',
      });
    }

    return true;
  }
}
