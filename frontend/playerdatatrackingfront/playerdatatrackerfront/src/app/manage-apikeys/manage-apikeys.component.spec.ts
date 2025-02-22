import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ManageApikeysComponent } from './manage-apikeys.component';

describe('ManageApikeysComponent', () => {
  let component: ManageApikeysComponent;
  let fixture: ComponentFixture<ManageApikeysComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ManageApikeysComponent]
    });
    fixture = TestBed.createComponent(ManageApikeysComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
