import { Component, OnInit } from '@angular/core';
import { TeamService } from '../team.service';

@Component({
  selector: 'app-team-page',
  templateUrl: './team-page.component.html',
  styleUrls: ['./team-page.component.css'],
})
export class TeamPageComponent implements OnInit {
  // Team Details
  teamDetails = { teamName: '', productFocus: '' };

  // Bookmarks
  bookmarks: any[] = [];
  newBookmark = { title: '', description: '' };

  // FAQ
  faqs: any[] = [];
  newFAQ = { question: '', answer: '' };

  constructor(private teamService: TeamService) {}

  ngOnInit() {
    this.getTeamDetails();
    this.getBookmarks();
    this.getFAQs();
  }

  // Team Details
  getTeamDetails() {
    this.teamService.getTeamDetails().subscribe((data) => {
      this.teamDetails = data;
    });
  }

  updateTeamDetails() {
    this.teamService.updateTeamDetails(this.teamDetails).subscribe(() => {
      alert('Team Details Updated Successfully');
    });
  }

  // Bookmarks
  getBookmarks() {
    this.teamService.getBookmarks().subscribe((data) => {
      this.bookmarks = data;
    });
  }

  addBookmark() {
    this.teamService.addBookmark(this.newBookmark).subscribe(() => {
      this.getBookmarks();
      this.newBookmark = { title: '', description: '' };
    });
  }

  updateBookmark(bookmark: any) {
    this.teamService.updateBookmark(bookmark.id, bookmark).subscribe(() => {
      alert('Bookmark Updated Successfully');
    });
  }

  deleteBookmark(id: number) {
    this.teamService.deleteBookmark(id).subscribe(() => {
      this.getBookmarks();
    });
  }

  // FAQ
  getFAQs() {
    this.teamService.getFAQs().subscribe((data) => {
      this.faqs = data;
    });
  }

  addFAQ() {
    this.teamService.addFAQ(this.newFAQ).subscribe(() => {
      this.getFAQs();
      this.newFAQ = { question: '', answer: '' };
    });
  }

  updateFAQ(faq: any) {
    this.teamService.updateFAQ(faq.id, faq).subscribe(() => {
      alert('FAQ Updated Successfully');
    });
  }

  deleteFAQ(id: number) {
    this.teamService.deleteFAQ(id).subscribe(() => {
      this.getFAQs();
    });
  }
}


================================================================================


import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class TeamService {
  private apiUrl = 'http://localhost:3000'; // Replace with your actual API endpoint

  constructor(private http: HttpClient) {}

  // CRUD Operations for Team Details
  getTeamDetails(): Observable<any> {
    return this.http.get(`${this.apiUrl}/team-details`);
  }

  updateTeamDetails(data: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/team-details`, data);
  }

  // CRUD Operations for Bookmarks
  getBookmarks(): Observable<any> {
    return this.http.get(`${this.apiUrl}/bookmarks`);
  }

  addBookmark(bookmark: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/bookmarks`, bookmark);
  }

  updateBookmark(id: number, bookmark: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/bookmarks/${id}`, bookmark);
  }

  deleteBookmark(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/bookmarks/${id}`);
  }

  // CRUD Operations for FAQ
  getFAQs(): Observable<any> {
    return this.http.get(`${this.apiUrl}/faqs`);
  }

  addFAQ(faq: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/faqs`, faq);
  }

  updateFAQ(id: number, faq: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/faqs/${id}`, faq);
  }

  deleteFAQ(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/faqs/${id}`);
  }
}


==========================================

<div>
  <!-- Team Details Section -->
  <h2>Team Details</h2>
  <form>
    <label>Team Name:</label>
    <input [(ngModel)]="teamDetails.teamName" name="teamName" />
    <label>Product Focus:</label>
    <input [(ngModel)]="teamDetails.productFocus" name="productFocus" />
    <button type="button" (click)="updateTeamDetails()">Update</button>
  </form>

  <hr />

  <!-- Bookmarks Section -->
  <h2>Bookmarks</h2>
  <form>
    <input [(ngModel)]="newBookmark.title" placeholder="Title" name="title" />
    <input
      [(ngModel)]="newBookmark.description"
      placeholder="Description"
      name="description"
    />
    <button type="button" (click)="addBookmark()">Add Bookmark</button>
  </form>
  <table border="1">
    <tr>
      <th>Title</th>
      <th>Description</th>
      <th>Actions</th>
    </tr>
    <tr *ngFor="let bookmark of bookmarks">
      <td><input [(ngModel)]="bookmark.title" /></td>
      <td><input [(ngModel)]="bookmark.description" /></td>
      <td>
        <button (click)="updateBookmark(bookmark)">Edit</button>
        <button (click)="deleteBookmark(bookmark.id)">Delete</button>
      </td>
    </tr>
  </table>

  <hr />

  <!-- FAQ Section -->
  <h2>FAQs</h2>
  <form>
    <input
      [(ngModel)]="newFAQ.question"
      placeholder="Question"
      name="question"
    />
    <input [(ngModel)]="newFAQ.answer" placeholder="Answer" name="answer" />
    <button type="button" (click)="addFAQ()">Add FAQ</button>
  </form>
  <table border="1">
    <tr>
      <th>Question</th>
      <th>Answer</th>
      <th>Actions</th>
    </tr>
    <tr *ngFor="let faq of faqs">
      <td><input [(ngModel)]="faq.question" /></td>
      <td><input [(ngModel)]="faq.answer" /></td>
      <td>
        <button (click)="updateFAQ(faq)">Edit</button>
        <button (click)="deleteFAQ(faq.id)">Delete</button>
      </td>
    </tr>
  </table>
</div>
