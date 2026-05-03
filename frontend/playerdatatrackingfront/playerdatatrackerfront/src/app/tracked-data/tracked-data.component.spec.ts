import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { Router } from '@angular/router';
import { TrackedDataComponent } from './tracked-data.component';

describe('TrackedDataComponent', () => {
  let component: TrackedDataComponent;
  let fixture: ComponentFixture<TrackedDataComponent>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [TrackedDataComponent],
      imports: [RouterTestingModule]
    }).compileComponents();

    fixture = TestBed.createComponent(TrackedDataComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('navigateToSearchPlayers routes to /searchPlayers', () => {
    const spy = spyOn(router, 'navigate');
    component.navigateToSearchPlayers();
    expect(spy).toHaveBeenCalledWith(['/searchPlayers']);
  });

  it('navigateToManageIndexalDB routes to /manageIndexalDB', () => {
    const spy = spyOn(router, 'navigate');
    component.navigateToManageIndexalDB();
    expect(spy).toHaveBeenCalledWith(['/manageIndexalDB']);
  });

  it('navigateToManageApikeys routes to /manageApikeys', () => {
    const spy = spyOn(router, 'navigate');
    component.navigateToManageApikeys();
    expect(spy).toHaveBeenCalledWith(['/manageApikeys']);
  });

  it('navigateToSearchFixtures routes to /searchFixtures', () => {
    const spy = spyOn(router, 'navigate');
    component.navigateToSearchFixtures();
    expect(spy).toHaveBeenCalledWith(['/searchFixtures']);
  });

  it('renders 4 operation buttons', () => {
    const buttons = fixture.nativeElement.querySelectorAll('.operation-button');
    expect(buttons.length).toBe(4);
  });

  it('Search Fixture button is present', () => {
    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('.operation-button');
    const labels = Array.from(buttons).map(b => b.textContent?.trim());
    expect(labels).toContain('Search Fixture');
  });

  it('clicking Search Fixture button triggers navigation', () => {
    const spy = spyOn(router, 'navigate');
    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('.operation-button');
    const searchFixtureBtn = Array.from(buttons).find(b => b.textContent?.trim() === 'Search Fixture');

    expect(searchFixtureBtn).toBeTruthy();
    searchFixtureBtn!.click();

    expect(spy).toHaveBeenCalledWith(['/searchFixtures']);
  });
});
