import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { NgModel } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common'; 

import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { delay, of } from 'rxjs';


export interface Project {
  label: string;
  projectNumberRegex: string;
  projectNumberPatternType: string;
}

@Component({
  standalone: true,
  imports: [
    ButtonModule, InputTextModule, FormsModule, CommonModule
  ],
  selector: 'project-number-input',
  templateUrl: './project-number-input.component.html'
})
export class ProjectNumberInputComponent implements OnInit {

  private cdr = inject(ChangeDetectorRef);

  project: Project | null = null;

  invalidProjectNumberMessage: string = '';

  ngOnInit(): void {
    this.loadProject();
  }

  private loadProject(): void {
    const mockData: Project = {
      label: '', 
      projectNumberRegex: '^.*$',     
      projectNumberPatternType: 'ANY' 
    };

    /*this.http.get<ProjectResponse>('/api/project/your-uuid')*/of(mockData).pipe(delay(800)).subscribe({
      next: (project: Project) => {
        this.project = project;

        of(mockData).pipe(delay(800)).subscribe({
          next: (project: Project) => {
            this.project = project;

            this.invalidProjectNumberMessage = this.getInvalidprojectNumberMessage();

            this.cdr.markForCheck();
          }
        });
      },
      error: (err) => {
        console.error('Fehler beim Laden', err);
      }
    });
  }

  private getInvalidprojectNumberMessage(): string {
    if (!this.project) return '';

    switch (this.project.projectNumberPatternType) {
      case 'NUMERIC_WITH_LEADING_ZEROS': 
        return 'Es sind nur Ziffern erlaubt (inkl. führender Nullen, z. B. 0042).';
      case 'NUMERIC_NO_LEADING_ZEROS': 
        return 'Die Projektnummer darf nur aus Ziffern bestehen und nicht mit einer 0 beginnen (z. B. 42).';
      case 'LETTERS_ONLY': 
        return 'Es sind ausschließlich Buchstaben erlaubt (keine Ziffern oder Sonderzeichen)';
      case 'ALPHANUMERIC': 
        return 'Es sind ausschließlich Buchstaben und Ziffern erlaubt (keine Sonderzeichen).';
      default: 
        return 'Das eingegebene Format ist ungültig.';
    }
  }

  onSave(projectNumberModel: NgModel): void {
    if (!this.project) return;

    if (projectNumberModel.invalid) {
      projectNumberModel.control.markAsTouched();
      return; 
    }
    console.log('Sende zum Backend:', this.project.label);
  }
}