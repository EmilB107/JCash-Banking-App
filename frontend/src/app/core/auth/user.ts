/** Mirrors com.jcash.app.users.dto.UserResponse. */
export interface User {
  id: string;
  email: string;
  mobileNumber: string;
  displayName: string;
  createdAt: string;
}

/** Body of POST /api/users (com.jcash.app.users.dto.CreateUserRequest). */
export interface RegisterRequest {
  email: string;
  mobileNumber: string;
  displayName: string;
  pin: string;
}
