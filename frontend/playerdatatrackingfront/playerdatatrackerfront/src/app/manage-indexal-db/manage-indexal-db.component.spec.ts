import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ManageIndexalDbComponent } from './manage-indexal-db.component';

describe('ManageIndexalDbComponent', () => {
  let component: ManageIndexalDbComponent;
  let fixture: ComponentFixture<ManageIndexalDbComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ManageIndexalDbComponent]
    });
    fixture = TestBed.createComponent(ManageIndexalDbComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
