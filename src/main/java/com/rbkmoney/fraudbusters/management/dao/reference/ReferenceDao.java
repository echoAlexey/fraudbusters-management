package com.rbkmoney.fraudbusters.management.dao.reference;

import com.rbkmoney.fraudbusters.management.domain.ReferenceModel;

import java.util.List;

public interface ReferenceDao {

    void insert(ReferenceModel referenceModel);

    void remove(String id);

    void remove(ReferenceModel referenceModel);

    ReferenceModel getById(String id);

    List<ReferenceModel> getList(int limit);

    List<ReferenceModel> getListByTemplateId(String templateId, int limit);
}