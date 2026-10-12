import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LeaveRequest, LeaveType } from '../../models/leave-request.model';
import { inclusiveDays, LeaveRequestFormComponent } from './leave-request-form.component';

describe('LeaveRequestFormComponent', () => {
  let component: LeaveRequestFormComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LeaveRequestFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    const fixture = TestBed.createComponent(LeaveRequestFormComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    http.expectOne('http://localhost:5080/api/employees')
      .flush([{ id: 1, name: 'Dana', annualQuota: 20 }]);
  });

  afterEach(() => http.verify());

  function fillValid(): void {
    component.form.setValue({
      employeeId: 1,
      type: LeaveType.Vacation,
      startDate: '2026-03-01',
      endDate: '2026-03-03'
    });
  }

  it('accepts Vacation (type 0) as a valid type', () => {
    fillValid();
    expect(component.form.controls.type.value).toBe(0);
    expect(component.form.valid).toBeTrue();
  });

  it('requires all fields', () => {
    expect(component.form.controls.employeeId.hasError('required')).toBeTrue();
    expect(component.form.controls.type.hasError('required')).toBeTrue();
    expect(component.form.controls.startDate.hasError('required')).toBeTrue();
    expect(component.form.controls.endDate.hasError('required')).toBeTrue();
  });

  it('rejects a start date after the end date', () => {
    fillValid();
    component.form.patchValue({ startDate: '2026-03-05', endDate: '2026-03-01' });
    expect(component.form.hasError('dateRange')).toBeTrue();
    expect(component.days).toBeNull();
  });

  it('counts inclusive calendar days', () => {
    expect(inclusiveDays('2026-03-01', '2026-03-01')).toBe(1);
    expect(inclusiveDays('2026-03-01', '2026-03-03')).toBe(3);
    // Spans the DST change in late March.
    expect(inclusiveDays('2026-03-25', '2026-04-02')).toBe(9);
  });

  it('posts without days, ignores a second submit while pending, and emits the result', () => {
    const emitted: LeaveRequest[] = [];
    component.created.subscribe((r) => emitted.push(r));
    fillValid();

    component.submit();
    component.submit();

    const req = http.expectOne('http://localhost:5080/api/leave-requests');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      employeeId: 1,
      type: LeaveType.Vacation,
      startDate: '2026-03-01',
      endDate: '2026-03-03'
    });

    req.flush({ id: 7, employeeId: 1, type: 0, startDate: '2026-03-01', endDate: '2026-03-03', status: 0, days: 3 });

    expect(emitted.length).toBe(1);
    expect(emitted[0].employee?.name).toBe('Dana');
    expect(component.submitting).toBeFalse();
    expect(component.successMessage).toContain('#7');
  });

  it('shows the API error message on failure', () => {
    fillValid();
    component.submit();

    http.expectOne('http://localhost:5080/api/leave-requests')
      .flush('Not enough vacation balance', { status: 400, statusText: 'Bad Request' });

    expect(component.submitError).toBe('Not enough vacation balance');
    expect(component.form.enabled).toBeTrue();
  });
});
