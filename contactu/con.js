<div>
  <h2>Work Details</h2>
  <form>
    <div>
      <label>Unity:</label>
      <input [(ngModel)]="workDetails.unity" name="unity" />
    </div>
    <div>
      <label>Email:</label>
      <input [(ngModel)]="workDetails.Email" name="email" type="email" />
    </div>
    <div>
      <label>Escalate:</label>
      <input [(ngModel)]="workDetails.Escalate" name="escalate" />
    </div>
    <button type="button" (click)="updateWorkDetails()">Update</button>
  </form>

  <hr />

  <h2>Work Locations</h2>
  <form>
    <div>
      <input [(ngModel)]="newLocation.place" name="place" placeholder="Place" />
      <input [(ngModel)]="newLocation.time" name="time" placeholder="Time" />
      <button type="button" (click)="addWorkLocation()">Add Location</button>
    </div>
  </form>

  <table border="1">
    <tr>
      <th>Place</th>
      <th>Time</th>
      <th>Actions</th>
    </tr>
    <tr *ngFor="let location of workDetails.WorkLocation; let i = index">
      <td><input [(ngModel)]="location.place" /></td>
      <td><input [(ngModel)]="location.time" /></td>
      <td>
        <button (click)="deleteWorkLocation(i)">Delete</button>
      </td>
    </tr>
  </table>
</div>
===========================

import { Component, OnInit } from '@angular/core';
import { WorkDetailsService } from '../work-details.service';

@Component({
  selector: 'app-work-details-page',
  templateUrl: './work-details-page.component.html',
  styleUrls: ['./work-details-page.component.css'],
})
export class WorkDetailsPageComponent implements OnInit {
  workDetails = {
    unity: '',
    Email: '',
    Escalate: '',
    WorkLocation: [{ place: '', time: '' }],
  };

  newLocation = { place: '', time: '' };

  constructor(private workDetailsService: WorkDetailsService) {}

  ngOnInit() {
    this.getWorkDetails();
  }

  // Fetch Work Details
  getWorkDetails() {
    this.workDetailsService.getWorkDetails().subscribe((data) => {
      this.workDetails = data;
    });
  }

  // Update Work Details
  updateWorkDetails() {
    this.workDetailsService.updateWorkDetails(this.workDetails).subscribe(() => {
      alert('Work Details Updated Successfully');
    });
  }

  // Add Work Location
  addWorkLocation() {
    if (this.newLocation.place.trim() && this.newLocation.time.trim()) {
      this.workDetails.WorkLocation.push({ ...this.newLocation });
      this.newLocation = { place: '', time: '' };
    }
  }

  // Delete Work Location
  deleteWorkLocation(index: number) {
    this.workDetails.WorkLocation.splice(index, 1);
  }
}
===============

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class WorkDetailsService {
  private apiUrl = 'http://localhost:3000/work-details'; // Replace with your API endpoint

  constructor(private http: HttpClient) {}

  // Fetch Work Details
  getWorkDetails(): Observable<any> {
    return this.http.get(this.apiUrl);
  }

  // Update Work Details
  updateWorkDetails(data: any): Observable<any> {
    return this.http.put(this.apiUrl, data);
  }

  // Add Work Location
  addWorkLocation(location: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/locations`, location);
  }

  // Delete Work Location
  deleteWorkLocation(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/locations/${id}`);
  }
}
