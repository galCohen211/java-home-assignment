// Intentionally thin. Part of the task is to introduce proper typing
// across the frontend instead of the `any` usage in the component.

// Numeric codes match how the API serializes the enums.
export enum LeaveType {
  Vacation = 0,
  Sick = 1,
  Unpaid = 2
}

export interface Employee {
  id: number;
  name: string;
  annualQuota: number;
}

export interface LeaveRequest {
  id: number;
  employeeId: number;
  // Populated by the API on reads; absent on the response to a create.
  employee?: Employee;
  type: LeaveType;
  startDate: string;
  endDate: string;
  status: number;
  days: number;
}

// Body of POST /api/leave-requests. The server computes `days` itself.
export interface CreateLeaveRequest {
  employeeId: number;
  type: LeaveType;
  startDate: string; // yyyy-MM-dd
  endDate: string;   // yyyy-MM-dd
}
