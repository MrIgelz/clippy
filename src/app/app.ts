import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ProductionNumberInputComponent } from './product-number-format-builder/project-number-input.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ProductionNumberInputComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('ProductNumber');
}
