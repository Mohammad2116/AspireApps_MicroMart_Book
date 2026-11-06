package book.companion.outbox;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class OutboxStore {
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final int leaseSeconds, maxAttempts;

  public OutboxStore(
      JdbcTemplate db,
      PlatformTransactionManager manager,
      @Value("${app.relay.lease-seconds:30}") int leaseSeconds,
      @Value("${app.relay.max-attempts:8}") int maxAttempts) {
    if (leaseSeconds < 15 || maxAttempts < 1)
      throw new IllegalArgumentException("Unsafe relay bounds");
    this.db = db;
    this.leaseSeconds = leaseSeconds;
    this.maxAttempts = maxAttempts;
    this.tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setTimeout(5);
  }

  public record Claim(UUID id, UUID aggregate, String payload, UUID token, int attempt) {}

  public Optional<Claim> claim() {
    return tx.execute(
        s -> {
          db.update(
              "update outbox set status='FAILED',ownership_token=null,lease_until=null,last_error='LEASE_RETRY_EXHAUSTED' where status='CLAIMED' and lease_until<clock_timestamp() and attempts>=?",
              maxAttempts);
          UUID token = UUID.randomUUID();
          var rows =
              db.query(
                  """
   WITH candidate AS (SELECT id FROM outbox WHERE published_at IS NULL AND attempts<?
    AND ((status='PENDING' AND next_attempt_at<=clock_timestamp()) OR (status='CLAIMED' AND lease_until<clock_timestamp()))
    ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED)
   UPDATE outbox o SET status='CLAIMED',ownership_token=?,lease_until=clock_timestamp()+make_interval(secs=>?),attempts=attempts+1
   FROM candidate c WHERE o.id=c.id RETURNING o.id,o.aggregate_id,o.payload,o.ownership_token,o.attempts
   """,
                  (r, n) ->
                      new Claim(
                          r.getObject(1, UUID.class),
                          r.getObject(2, UUID.class),
                          r.getString(3),
                          r.getObject(4, UUID.class),
                          r.getInt(5)),
                  maxAttempts,
                  token,
                  leaseSeconds);
          return rows.stream().findFirst();
        });
  }

  public boolean delivered(Claim c) {
    return Boolean.TRUE.equals(
        tx.execute(
            s ->
                db.update(
                        "update outbox set status='PUBLISHED',published_at=clock_timestamp(),ownership_token=null,lease_until=null,last_error=null where id=? and ownership_token=? and status='CLAIMED' and lease_until>clock_timestamp()",
                        c.id(),
                        c.token())
                    == 1));
  }

  public boolean failed(Claim c) {
    return Boolean.TRUE.equals(
        tx.execute(
            s ->
                db.update(
                        "update outbox set status=case when attempts>=? then 'FAILED' else 'PENDING' end,next_attempt_at=clock_timestamp()+make_interval(secs=>?),ownership_token=null,lease_until=null,last_error='SEND_UNCONFIRMED' where id=? and ownership_token=? and status='CLAIMED'",
                        maxAttempts,
                        Math.min(60, 1 << Math.min(c.attempt(), 5)),
                        c.id(),
                        c.token())
                    == 1));
  }
}
