import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TrackedDataComponent } from './tracked-data.component';

describe('TrackedDataComponent', () => {
  let component: TrackedDataComponent;
  let fixture: ComponentFixture<TrackedDataComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [TrackedDataComponent]
    });
    fixture = TestBed.createComponent(TrackedDataComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
