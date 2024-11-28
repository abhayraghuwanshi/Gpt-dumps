import { Component, OnInit } from '@angular/core';
import { ProjectService } from '../project.service';

@Component({
  selector: 'app-project-page',
  templateUrl: './project-page.component.html',
  styleUrls: ['./project-page.component.css'],
})
export class ProjectPageComponent implements OnInit {
  // TechStack
  techStack: string[] = [];
  newTech = '';

  // Projects
  projects: any[] = [];
  newProject = { title: '', url: '', description: '' };

  // Dependencies
  dependencies: any[] = [];
  newDependency = { teamDependency: [], techTeamDependency: [] };

  constructor(private projectService: ProjectService) {}

  ngOnInit() {
    this.getTechStack();
    this.getProjects();
    this.getDependencies();
  }

  // TechStack
  getTechStack() {
    this.projectService.getTechStack().subscribe((data) => {
      this.techStack = data;
    });
  }

  updateTechStack() {
    this.projectService.updateTechStack(this.techStack).subscribe(() => {
      alert('TechStack Updated Successfully');
    });
  }

  addTechStack() {
    if (this.newTech.trim()) {
      this.techStack.push(this.newTech);
      this.updateTechStack();
      this.newTech = '';
    }
  }

  deleteTechStack(index: number) {
    this.techStack.splice(index, 1);
    this.updateTechStack();
  }

  // Projects
  getProjects() {
    this.projectService.getProjects().subscribe((data) => {
      this.projects = data;
    });
  }

  addProject() {
    this.projectService.addProject(this.newProject).subscribe(() => {
      this.getProjects();
      this.newProject = { title: '', url: '', description: '' };
    });
  }

  updateProject(project: any) {
    this.projectService.updateProject(project.id, project).subscribe(() => {
      alert('Project Updated Successfully');
    });
  }

  deleteProject(id: number) {
    this.projectService.deleteProject(id).subscribe(() => {
      this.getProjects();
    });
  }

  // Dependencies
  getDependencies() {
    this.projectService.getDependencies().subscribe((data) => {
      this.dependencies = data;
    });
  }

  updateDependencies() {
    this.projectService.updateDependencies(this.dependencies).subscribe(() => {
      alert('Dependencies Updated Successfully');
    });
  }
}
=============================================

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class ProjectService {
  private apiUrl = 'http://localhost:3000'; // Replace with your API endpoint

  constructor(private http: HttpClient) {}

  // CRUD for TechStack
  getTechStack(): Observable<any> {
    return this.http.get(`${this.apiUrl}/techstack`);
  }

  updateTechStack(techStack: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/techstack`, { techStack });
  }

  // CRUD for Project
  getProjects(): Observable<any> {
    return this.http.get(`${this.apiUrl}/projects`);
  }

  addProject(project: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/projects`, project);
  }

  updateProject(id: number, project: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/projects/${id}`, project);
  }

  deleteProject(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/projects/${id}`);
  }

  // CRUD for Dependency
  getDependencies(): Observable<any> {
    return this.http.get(`${this.apiUrl}/dependencies`);
  }

  updateDependencies(dependency: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/dependencies`, dependency);
  }
}
=========================
<div>
  <!-- TechStack Section -->
  <h2>Tech Stack</h2>
  <form>
    <input [(ngModel)]="newTech" placeholder="Add Tech" name="tech" />
    <button type="button" (click)="addTechStack()">Add Tech</button>
  </form>
  <table border="1">
    <tr>
      <th>Technology</th>
      <th>Actions</th>
    </tr>
    <tr *ngFor="let tech of techStack; let i = index">
      <td>{{ tech }}</td>
      <td>
        <button (click)="deleteTechStack(i)">Delete</button>
      </td>
    </tr>
  </table>

  <hr />

  <!-- Project Section -->
  <h2>Projects</h2>
  <form>
    <input [(ngModel)]="newProject.title" placeholder="Title" name="title" />
    <input [(ngModel)]="newProject.url" placeholder="URL" name="url" />
    <input
      [(ngModel)]="newProject.description"
      placeholder="Description"
      name="description"
    />
    <button type="button" (click)="addProject()">Add Project</button>
  </form>
  <table border="1">
    <tr>
      <th>Title</th>
      <th>URL</th>
      <th>Description</th>
      <th>Actions</th>
    </tr>
    <tr *ngFor="let project of projects">
      <td><input [(ngModel)]="project.title" /></td>
      <td><input [(ngModel)]="project.url" /></td>
      <td><input [(ngModel)]="project.description" /></td>
      <td>
        <button (click)="updateProject(project)">Edit</button>
        <button (click)="deleteProject(project.id)">Delete</button>
      </td>
    </tr>
  </table>

  <hr />

  <!-- Dependency Section -->
  <h2>Dependencies</h2>
  <table border="1">
    <tr>
      <th>Team Dependencies</th>
      <th>Tech Team Dependencies</th>
      <th>Actions</th>
    </tr>
    <tr *ngFor="let dependency of dependencies">
      <td>
        <input [(ngModel)]="dependency.teamDependency" />
      </td>
      <td>
        <input [(ngModel)]="dependency.techTeamDependency" />
      </td>
      <td>
        <button (click)="updateDependencies()">Save</button>
      </td>
    </tr>
  </table>
</div>
