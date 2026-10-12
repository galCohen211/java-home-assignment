import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LeaveRequest } from '../models/leave-request.model';
import { LeaveApiService } from './leave-api.service';
import { LeaveRequestFormComponent } from './leave-request-form/leave-request-form.component';

// Newest start date first, matching the order the API returns.
function byStartDateDesc(a: LeaveRequest, b: LeaveRequest): number {
  return b.startDate.localeCompare(a.startDate);
}

@Component({
  selector: 'app-leave-requests',
  standalone: true,
  imports: [CommonModule, LeaveRequestFormComponent],
  templateUrl: './leave-requests.component.html',
  styleUrls: ['./leave-requests.component.css']
})
export class LeaveRequestsComponent implements OnInit {
  requests: LeaveRequest[] = [];
  loading = false;

  constructor(private api: LeaveApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.getLeaveRequests().subscribe((data) => {
      this.requests = data;
      this.loading = false;
    });
  }

  // Insert the new request in place instead of reloading the whole list.
  onCreated(request: LeaveRequest): void {
    this.requests = [...this.requests, request].sort(byStartDateDesc);
  }

  // Wired up by the candidate as part of the assignment.
  approve(id: number): void {
    // TODO (candidate): call POST /api/leave-requests/{id}/approve
    // and handle loading / error / success without a generic alert.
    this.api.approve(id).subscribe(() => {
      this.load();
    });
  }

  typeLabel(type: number): string {
    if (type == 0) return 'Vacation';
    if (type == 1) return 'Sick';
    return 'Unpaid';
  }

  statusLabel(status: number): string {
    if (status == 0) return 'Pending';
    if (status == 1) return 'Approved';
    return 'Rejected';
  }
}
