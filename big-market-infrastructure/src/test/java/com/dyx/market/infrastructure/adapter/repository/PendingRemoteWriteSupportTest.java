package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.infrastructure.dao.IPendingRemoteWriteTaskDao;
import com.dyx.market.infrastructure.dao.po.PendingRemoteWriteTask;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PendingRemoteWriteSupportTest {

    private IPendingRemoteWriteTaskDao dao;
    private PendingRemoteWriteSupport support;

    @Before
    public void setUp() {
        dao = mock(IPendingRemoteWriteTaskDao.class);
        support = new PendingRemoteWriteSupport();
        try {
            java.lang.reflect.Field field = PendingRemoteWriteSupport.class.getDeclaredField("pendingRemoteWriteTaskDao");
            field.setAccessible(true);
            field.set(support, dao);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void enqueue_should_return_false_when_args_invalid() {
        assertFalse(support.enqueue("", "op", new Object()));
        assertFalse(support.enqueue("obn", "", new Object()));
        assertFalse(support.enqueue("obn", "op", null));
    }

    @Test
    public void enqueue_should_return_true_on_success() {
        assertTrue(support.enqueue("obn-1", "credit_create", new Object()));
        verify(dao).insert(any(PendingRemoteWriteTask.class));
    }

    @Test
    public void enqueue_should_propagate_db_failure() {
        doThrow(new RuntimeException("db down")).when(dao).insert(any(PendingRemoteWriteTask.class));
        try {
            support.enqueue("obn-2", "credit_create", new Object());
            fail("expected exception");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("db down"));
        }
    }
}
