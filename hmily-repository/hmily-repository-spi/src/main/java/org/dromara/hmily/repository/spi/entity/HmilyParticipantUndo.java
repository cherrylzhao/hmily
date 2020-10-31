/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.hmily.repository.spi.entity;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The type Hmily participant undo.
 *
 * @author xiaoyu
 */
@Data
@EqualsAndHashCode
public final class HmilyParticipantUndo implements Serializable {
    
    private static final long serialVersionUID = 6869663782220867407L;
    /**
     * undo id.
     */
    private Long undoId;
    
    /**
     * participant id.
     */
    private Long participantId;
    
    /**
     * transaction id.
     */
    private Long transId;
    
    /**
     * resource id.
     */
    private String resourceId;
    
    /**
     * status.
     */
    private Integer status;
    
    /**
     * data dataSnapshot.
     */
    private HmilyDataSnapshot dataSnapshot;
    
    /**
     * createTime.
     */
    private Date createTime;
    
    /**
     * updateTime.
     */
    private Date updateTime;
    
    public HmilyParticipantUndo() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
