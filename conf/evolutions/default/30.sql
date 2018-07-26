# --- !Ups

/* Vehicle */
alter table vehicles
  drop constraint if exists vehicles_user_id_fkey;
alter table vehicles
  add constraint vehicles_user_id_fkey foreign key (user_id) references users (id) on delete cascade;

/* Task */
alter table tasks
  drop constraint if exists jobs_user_id_fkey;
alter table tasks
  add constraint tasks_user_id_fkey foreign key (user_id) references users (id) on delete cascade;

alter table tasks
  drop constraint if exists jobs_vehicle_id_fkey;
alter table tasks
  add constraint tasks_vehicle_id_fkey foreign key (vehicle_id) references vehicles (id) on delete cascade;

alter table tasks
    rename constraint jobs_agent_id_fkey to tasks_agent_id_fkey;

alter table tasks
    rename constraint jobs_job_id_key to tasks_job_id_key;

alter table tasks
    rename constraint jobs_pkey to tasks_pkey;

/* Payment details */
alter table payment_details
  drop constraint if exists payment_details_task_id_fkey;
alter table payment_details
  add constraint payment_details_task_id_fkey foreign key (task_id) references tasks (id) on delete cascade;

/* Task services */
alter table task_services
  drop constraint if exists task_services_job_id_fkey;
alter table task_services
  add constraint task_services_task_id_fkey foreign key (task_id) references tasks (id) on delete cascade;

/* Locations */
alter table locations
  drop constraint if exists locations_user_id_fkey;
alter table locations
  add constraint locations_user_id_fkey foreign key (user_id) references users (id) on delete cascade;