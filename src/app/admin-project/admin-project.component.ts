import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RadioButtonModule } from 'primeng/radiobutton';
import { ButtonModule } from 'primeng/button';
import { delay, of } from 'rxjs';

export interface AdminProjectResponse {
  project: {
    projectNumberRegex: string;
    projectNumberPatternType: string;
  }
}

@Component({
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule, 
    RadioButtonModule,
    ButtonModule
  ],
  selector: 'admin-project',
  templateUrl: './admin-project.component.html'
})
export class AdminValidationConfigComponent implements OnInit {

  company: AdminProjectResponse | null = null;

  ngOnInit(): void {
    this.loadProject();
  }

  private loadProject(): void {
    const mockAdminData: AdminProjectResponse = {
      project: {
        projectNumberRegex: '^.*$',
        projectNumberPatternType: 'NUMERIC_WITH_LEADING_ZEROS'
      }
    };

    /* this.http.get<AdminProjectResponse>('/api/admin/project/uuid-1234/config') */
    of(mockAdminData).pipe(delay(800)).subscribe({
      next: (company: AdminProjectResponse) => {
        this.company = company;
      },
      error: (err) => {
        console.error('Fehler beim Laden der Admin-Config', err);
      }
    });
  }

  onSave(): void {
    console.log('Speichere Projekteinstellungen', this.company?.project.projectNumberPatternType);
    /*
    const payload = { patternType: this.company?.project.projectNumberPatternType };
    this.http.post('/api/admin/project/config', payload).subscribe(...);
    */
  }
}