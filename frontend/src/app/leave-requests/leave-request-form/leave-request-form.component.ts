import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { finalize } from 'rxjs';
import { CreateLeaveRequest, Employee, LeaveRequest, LeaveType } from '../../models/leave-request.model';
import { LeaveApiService } from '../leave-api.service';

const MS_PER_DAY = 24 * 60 * 60 * 1000;

// Inclusive calendar days between two yyyy-MM-dd dates, or null if either is
// missing or the range is reversed. Uses UTC so DST changes don't skew the count.
export function inclusiveDays(start: string, end: string): number | null {
  if (!start || !end) return null;
  const [sy, sm, sd] = start.split('-').map(Number);
  const [ey, em, ed] = end.split('-').map(Number);
  const days = (Date.UTC(ey, em - 1, ed) - Date.UTC(sy, sm - 1, sd)) / MS_PER_DAY + 1;
  return days >= 1 ? days : null;
}

// Group validator: start must be on or before end. Missing dates are left to
// Validators.required. yyyy-MM-dd strings compare correctly as text.
const dateRangeValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const start = group.get('startDate')?.value as string;
  const end = group.get('endDate')?.value as string;
  return start && end && start > end ? { dateRange: true } : null;
};

@Component({
  selector: 'app-leave-request-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './leave-request-form.component.html',
  styleUrls: ['./leave-request-form.component.css']
})
export class LeaveRequestFormComponent implements OnInit {
  // Emits the created request, with `employee` filled in so the list can show the name.
  @Output() created = new EventEmitter<LeaveRequest>();

  readonly leaveTypes = [
    { value: LeaveType.Vacation, label: 'Vacation' },
    { value: LeaveType.Sick, label: 'Sick' },
    { value: LeaveType.Unpaid, label: 'Unpaid' }
  ];

  readonly form = new FormGroup(
    {
      employeeId: new FormControl<number | null>(null, Validators.required),
      // LeaveType.Vacation is 0; Validators.required only rejects null/'' so 0 is valid.
      type: new FormControl<LeaveType | null>(null, Validators.required),
      startDate: new FormControl('', { nonNullable: true, validators: Validators.required }),
      endDate: new FormControl('', { nonNullable: true, validators: Validators.required })
    },
    { validators: dateRangeValidator }
  );

  employees: Employee[] = [];
  employeesLoading = false;
  employeesError: string | null = null;

  submitting = false;
  submitError: string | null = null;
  successMessage: string | null = null;

  constructor(private api: LeaveApiService) {}

  ngOnInit(): void {
    this.loadEmployees();
  }

  loadEmployees(): void {
    this.employeesLoading = true;
    this.employeesError = null;
    this.api
      .getEmployees()
      .pipe(finalize(() => (this.employeesLoading = false)))
      .subscribe({
        next: (employees) => (this.employees = employees),
        error: () => (this.employeesError = 'Could not load employees.')
      });
  }

  get days(): number | null {
    const { startDate, endDate } = this.form.getRawValue();
    return inclusiveDays(startDate, endDate);
  }

  // Show a control's errors only after the user has interacted with it (or tried to submit).
  showError(name: 'employeeId' | 'type' | 'startDate' | 'endDate'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  get showDateRangeError(): boolean {
    const { startDate, endDate } = this.form.controls;
    return this.form.hasError('dateRange') && (startDate.touched || endDate.touched);
  }

  submit(): void {
    if (this.submitting) return;

    this.successMessage = null;
    this.submitError = null;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { employeeId, type, startDate, endDate } = this.form.getRawValue();
    // Narrowed by the validators above.
    const body: CreateLeaveRequest = { employeeId: employeeId!, type: type!, startDate, endDate };

    this.submitting = true;
    this.form.disable({ emitEvent: false });

    this.api
      .createLeaveRequest(body)
      .pipe(
        finalize(() => {
          this.submitting = false;
          this.form.enable({ emitEvent: false });
        })
      )
      .subscribe({
        next: (request) => {
          const employee = this.employees.find((e) => e.id === request.employeeId);
          this.created.emit({ ...request, employee });
          this.successMessage = `Request #${request.id} created for ${employee?.name ?? 'employee'} (${request.days} days).`;
          this.form.reset();
        },
        error: (err: HttpErrorResponse) => (this.submitError = this.errorMessage(err))
      });
  }

  // The API returns plain-text messages for 400/404/409.
  private errorMessage(err: HttpErrorResponse): string {
    if (err.status === 0) return 'Could not reach the server. Please try again.';
    if (typeof err.error === 'string' && err.error.trim()) return err.error;
    return 'Something went wrong while creating the request.';
  }
}
