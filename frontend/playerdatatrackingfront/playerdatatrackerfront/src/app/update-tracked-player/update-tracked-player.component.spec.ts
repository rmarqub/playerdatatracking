import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UpdateTrackedPlayerComponent } from './update-tracked-player.component';

describe('UpdateTrackedPlayerComponent', () => {
  let component: UpdateTrackedPlayerComponent;
  let fixture: ComponentFixture<UpdateTrackedPlayerComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [UpdateTrackedPlayerComponent]
    });
    fixture = TestBed.createComponent(UpdateTrackedPlayerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
